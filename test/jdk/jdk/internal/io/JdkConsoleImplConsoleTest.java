/**
 * @test
 * @modules java.base/jdk.internal.io
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Objects;
import jdk.internal.io.JdkConsoleImpl;

public class JdkConsoleImplConsoleTest {
    public static void main(String... args) throws IOException {
        new JdkConsoleImplConsoleTest().run();
    }

    private void run() throws IOException {
        testNavigation();
    }

    private void testNavigation() throws IOException {
        String input = """
                       12345\033[D\033[D\033[3~6\033[1~7\033[4~8\033[H9\033[FA\r
                       """;
        String expectedResult = "97123658A";
        char[] read = JdkConsoleImpl.doRead(new StringReader(input), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(expectedResult, new String(read));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected: " + expected +
                                     "actual: " + actual);
        }
    }
}
