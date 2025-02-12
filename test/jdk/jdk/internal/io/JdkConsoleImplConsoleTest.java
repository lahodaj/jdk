/**
 * @test
 * @modules jdk.internal.le/jdk.internal.console
 * @run main JdkConsoleImplConsoleTest
 */

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.internal.console.SimpleConsoleReader;

public class JdkConsoleImplConsoleTest {
    public static void main(String... args) throws IOException {
        new JdkConsoleImplConsoleTest().run();
    }

    private void run() throws IOException {
        // testNavigation();
        testTerminalHandling();
    }

    private void testNavigation() throws IOException {
        String input = """
                       12345\033[D\033[D\033[3~6\033[1~7\033[4~8\033[H9\033[FA\r
                       """;
        String expectedResult = "97123658A";
        char[] read = SimpleConsoleReader.doRead(new StringReader(input), new StringWriter(), false, 0, () -> Integer.MAX_VALUE);
        assertEquals(expectedResult, new String(read));
    }

    private void testTerminalHandling() throws IOException {
        Terminal terminal = new Terminal(5, 5);
        Thread.ofVirtual().start(() -> {
            try {
                SimpleConsoleReader.doRead(terminal.getInput(), terminal.getOutput(), false, 0, () -> terminal.width);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        });

        terminal.typed("123456");
        assertEquals("""
                     12345
                     6


                     """,
                     terminal.getDisplay());
        terminal.typed("\033[D\033[D\033[DN");
        assertEquals("""
                     123N4
                     56


                     """,
                     terminal.getDisplay());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected: " + expected +
                                     "actual: " + actual);
        }
    }

    private static class Terminal {
        private final Map<Character, Object> bindings = new HashMap<>();
        private final int width;
        private final char[][] buffer;
        private final StringBuilder pendingOutput = new StringBuilder();
        private final StringBuilder pendingInput = new StringBuilder();
        private final Object emptyInputLock = new Object();
        private Map<Character, Object> currentBindings = bindings;
        private int cursorX;
        private int cursorY;

        public Terminal(int width, int height) {
            this.width = width;
            this.buffer = new char[height][];

            for (int i = 0; i < height; i++) {
                this.buffer[i] = createLine();
            }

            cursorX = 1;
            cursorY = 1;

            // addKeyBinding("\033[D", () -> cursorX = Math.max(cursorX - 1, 0));
            addKeyBinding("\033[A", () -> cursorY = Math.max(cursorY - 1, 1));
            addKeyBinding("\033[B", () -> cursorY = Math.min(cursorY + 1, buffer.length));
            addKeyBinding("\033[1G", () -> cursorX = 1);
            addKeyBinding("\033[2G", () -> cursorX = 2);
            addKeyBinding("\033[3G", () -> cursorX = 3);
            addKeyBinding("\033[4G", () -> cursorX = 4);
            addKeyBinding("\033[5G", () -> cursorX = 5);
            addKeyBinding("\033[K", () -> Arrays.fill(buffer[cursorY - 1], cursorX - 1, buffer[cursorY - 1].length, ' '));
            addKeyBinding("\n", () -> {
                cursorY++;
                if (cursorY > buffer.length) {
                    throw new AssertionError("scrolling via \\n not implemented!");
                }
            });
            addKeyBinding("\r", () -> cursorX = 1);
        }

        private char[] createLine() {
            char[] line = new char[width];

            Arrays.fill(line, ' ');

            return line;
        }

        private void addKeyBinding(String sequence, Runnable action) {
            Map<Character, Object> pending = bindings;

            for (int i = 0; i < sequence.length() - 1; i++) {
                pending = (Map<Character, Object>) pending.computeIfAbsent(sequence.charAt(i), _ -> new HashMap<>());
            }

            if (pending.put(sequence.charAt(sequence.length() - 1), action) != null) {
                throw new IllegalStateException();
            }
        }

        private void handleOutput(char c) {
            pendingOutput.append(c);

            Object nestedBindings = currentBindings.get(c);

            switch (nestedBindings) {
                case null -> {
                    if (pendingOutput.length() > 1) {
                        System.out.println("Hu!");
                    }
                    for (int i = 0; i < pendingOutput.length(); i++) {
                        if (cursorX > buffer[0].length) { //(width)
                            cursorX = 1;
                            cursorY++;
                            scrollIfNeeded();
                        }

                        buffer[cursorY - 1][cursorX - 1] = pendingOutput.charAt(i);
                        cursorX++;
                    }

                    pendingOutput.delete(0, pendingOutput.length());
                    currentBindings = bindings;
                }

                case Runnable r -> {
                    r.run();
                    pendingOutput.delete(0, pendingOutput.length());
                    currentBindings = bindings;
                }

                case Map nextBindings -> {
                    currentBindings = nextBindings;
                }

                default -> throw new IllegalStateException();
            }
        }

        private void scrollIfNeeded() {
            if (cursorY > buffer.length) {
                for (int j = 0; j < buffer.length - 1; j++) {
                    buffer[j] = buffer[j + 1];
                }

                buffer[buffer.length - 1] = createLine();
                cursorY--;
            }
        }

        public Writer getOutput() {
            return new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    for (int i = 0; i < len; i++) {
                        handleOutput(cbuf[i + off]);
                    }
                }

                @Override
                public void flush() throws IOException {}

                @Override
                public void close() throws IOException {}

            };
        }

        public Reader getInput() {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    if (len == 0) {
                        return 0;
                    }

                    synchronized (pendingInput) {
                        while (pendingInput.isEmpty()) {
                            synchronized (emptyInputLock) {
                                emptyInputLock.notifyAll();
                            }
                            try {
                                pendingInput.wait();
                            } catch (InterruptedException ex) {
                            }
                        }

                        cbuf[off] = pendingInput.charAt(0);
                        pendingInput.delete(0, 1);

                        return 1;
                    }
                }

                @Override
                public void close() throws IOException {}
            };
        }

        public void typed(String text) {
            synchronized (pendingInput) {
                pendingInput.append(text);
                pendingInput.notifyAll();
            }
            synchronized (emptyInputLock) {
                try {
                    emptyInputLock.wait();
                } catch (InterruptedException ex) {
                }
            }
        }

        public String getDisplay() {
            return Arrays.stream(buffer).map(String::new).map(l -> l.replaceAll(" +$", "")).collect(Collectors.joining("\n"));
        }
    }
}
