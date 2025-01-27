package jdk.internal.console;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;
import static jdk.internal.console.Kernel32.ALT_FLAG;
import jdk.internal.console.Kernel32.CONSOLE_SCREEN_BUFFER_INFO;
import static jdk.internal.console.Kernel32.CTRL_FLAG;
import jdk.internal.console.Kernel32.INPUT_RECORD;
import static jdk.internal.console.Kernel32.INVALID_HANDLE_VALUE;
import static jdk.internal.console.Kernel32.KEY_EVENT;
import jdk.internal.console.Kernel32.KEY_EVENT_RECORD;
import static jdk.internal.console.Kernel32.LEFT_ALT_PRESSED;
import static jdk.internal.console.Kernel32.LEFT_CTRL_PRESSED;
import static jdk.internal.console.Kernel32.RIGHT_ALT_PRESSED;
import static jdk.internal.console.Kernel32.RIGHT_CTRL_PRESSED;
import static jdk.internal.console.Kernel32.SHIFT_FLAG;
import static jdk.internal.console.Kernel32.SHIFT_PRESSED;
import static jdk.internal.console.Kernel32.WINDOW_BUFFER_SIZE_EVENT;

//partly based on JLine:
/*
 * Copyright (c) 2009-2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */

public class NativeConsoleReader {

    private static final int ENABLE_PROCESSED_INPUT = 0x0001; //for input
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200; //for input
    private static final int ENABLE_PROCESSED_OUTPUT = 0x0001; //for output
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004; //for output

    public static char[] readline(Reader reader, Writer out, boolean password) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inConsole = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
            MemorySegment originalInModeRef = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            Kernel32.GetConsoleMode(inConsole, originalInModeRef);
            int originalInMode = originalInModeRef.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
            Kernel32.SetConsoleMode(inConsole, ENABLE_PROCESSED_INPUT);
            MemorySegment outConsole = Kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
            MemorySegment originalOutModeRef = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT);
            Kernel32.GetConsoleMode(outConsole, originalOutModeRef);
            int originalOutMode = originalOutModeRef.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
            Kernel32.SetConsoleMode(outConsole, ENABLE_VIRTUAL_TERMINAL_PROCESSING | ENABLE_PROCESSED_OUTPUT);
            CONSOLE_SCREEN_BUFFER_INFO consoleInfo = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO(arena);
            Kernel32.GetConsoleScreenBufferInfo(outConsole, consoleInfo);
            try {
                AtomicInteger width = new AtomicInteger(consoleInfo.size().x());
                int firstLineOffset = consoleInfo.cursorPosition().x();
                InputStream in = new ConsoleInputStream(inConsole, () -> {
                    Kernel32.GetConsoleScreenBufferInfo(outConsole, consoleInfo);
                    width.set(consoleInfo.size().x());
                });
                return SimpleConsoleReader.doRead(new InputStreamReader(in), out, password, firstLineOffset, () -> width.get());
            } finally {
                Kernel32.SetConsoleMode(inConsole, originalInMode);
                Kernel32.SetConsoleMode(outConsole, originalOutMode);
            }
        }
    }

    private static final class ConsoleInputStream extends InputStream {

        private final MemorySegment inConsole;
        private final Runnable refreshWidth;
        private int[] backlog;
        private int backlogIndex;

        public ConsoleInputStream(MemorySegment inConsole, Runnable refreshWidth) {
            this.inConsole = inConsole;
            this.refreshWidth = refreshWidth;
        }

        @Override
        public int read() throws IOException {
            while (backlog == null || backlogIndex >= backlog.length) {
                try (Arena arena = Arena.ofConfined()) {
                    INPUT_RECORD[] events;
                    if (inConsole != null
                            && inConsole.address() != INVALID_HANDLE_VALUE
                            && Kernel32.WaitForSingleObject(inConsole, 100) == 0) { //TODO: 100ms timeout sensible?
                        events = Kernel32.readConsoleInputHelper(arena, inConsole, 1, false);
                    } else {
                        return -1;
                    }

                    for (INPUT_RECORD event : events) {
                        int eventType = event.eventType();
                        if (eventType == KEY_EVENT) {
                            KEY_EVENT_RECORD keyEvent = event.keyEvent();
                            processKeyEvent(
                                    keyEvent.keyDown(), keyEvent.keyCode(), keyEvent.uchar(), keyEvent.controlKeyState());
                        } else if (eventType == WINDOW_BUFFER_SIZE_EVENT) {
                            refreshWidth.run();
                            return -1; //repaint - can be better?
                        }
                    }
                }
            }
            return backlog[backlogIndex++];
        }

        protected void processKeyEvent(
                final boolean isKeyDown, final short virtualKeyCode, char ch, final int controlKeyState)
                throws IOException {
            StringBuilder data = new StringBuilder();
            final boolean isCtrl = (controlKeyState & (RIGHT_CTRL_PRESSED | LEFT_CTRL_PRESSED)) > 0;
            final boolean isAlt = (controlKeyState & (RIGHT_ALT_PRESSED | LEFT_ALT_PRESSED)) > 0;
            final boolean isShift = (controlKeyState & SHIFT_PRESSED) > 0;
            // key down event
            if (isKeyDown && ch != '\3') {
                // Pressing "Alt Gr" is translated to Alt-Ctrl, hence it has to be checked that Ctrl is _not_ pressed,
                // otherwise inserting of "Alt Gr" codes on non-US keyboards would yield errors
                if (ch != 0
                        && (controlKeyState
                                        & (RIGHT_ALT_PRESSED | LEFT_ALT_PRESSED | RIGHT_CTRL_PRESSED | LEFT_CTRL_PRESSED))
                                == (RIGHT_ALT_PRESSED | LEFT_CTRL_PRESSED)) {
                    data.append(ch);
                } else {
                    final String keySeq = getEscapeSequence(
                            virtualKeyCode, (isCtrl ? CTRL_FLAG : 0) + (isAlt ? ALT_FLAG : 0) + (isShift ? SHIFT_FLAG : 0));
                    if (keySeq != null) {
                        data.append(keySeq);
                    } else {
                    /* uchar value in Windows when CTRL is pressed:
                     * 1). Ctrl +  <0x41 to 0x5e>      : uchar=<keyCode> - 'A' + 1
                     * 2). Ctrl + Backspace(0x08)      : uchar=0x7f
                     * 3). Ctrl + Enter(0x0d)          : uchar=0x0a
                     * 4). Ctrl + Space(0x20)          : uchar=0x20
                     * 5). Ctrl + <Other key>          : uchar=0
                     * 6). Ctrl + Alt + <Any key>      : uchar=0
                     */
                    if (ch > 0) {
                        if (isAlt) {
                            data.append('\033');
                        }
                        if (isCtrl && ch != ' ' && ch != '\n' && ch != 0x7f) {
                            data.append((char) (ch == '?' ? 0x7f : Character.toUpperCase(ch) & 0x1f));
                        } else if (isCtrl && ch == '\n') {
                            //simulate Alt-Enter:
                            data.append('\033');
                            data.append('\r');
                        } else {
                            data.append(ch);
                        }
                    } else if (isCtrl) { // Handles the ctrl key events(uchar=0)
                        if (virtualKeyCode >= 'A' && virtualKeyCode <= 'Z') {
                            ch = (char) (virtualKeyCode - 0x40);
                        } else if (virtualKeyCode == 191) { // ?
                            ch = 127;
                        }
                        if (ch > 0) {
                            if (isAlt) {
                                data.append('\033');
                            }
                            data.append(ch);
                        }
                    }
                    }
                }
            } else if (isKeyDown && ch == '\3') {
                data.append('\3');
            }
            // key up event
            else {
                // support ALT+NumPad input method
                if (virtualKeyCode == 0x12 /*VK_MENU ALT key*/ && ch > 0) {
                    data.append(ch); // no such combination in Windows
                }
            }
            backlog = new int[data.length()];
            for (int i = 0; i < data.length(); i++) {
                backlog[i] = data.charAt(i);
            }
            backlogIndex = 0;
        }

        protected String getEscapeSequence(short keyCode, int keyState) {
            // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
            // TODO: numpad keys, modifiers
            String escapeSequence = null;
            switch (keyCode) {
                case 0x08: // VK_BACK BackSpace
                    escapeSequence = "\u007F";
                    break;
                case 0x09:
                    return null;
                case 0x23: // VK_END
                    escapeSequence = "\033[F";
                    break;
                case 0x24: // VK_HOME
                    escapeSequence = "\033[H";
                    break;
                case 0x25: // VK_LEFT
                    escapeSequence = "\033[D";
                    break;
                case 0x27: // VK_RIGHT
                    escapeSequence = "\033[C";
                    break;
                case 0x2E: // VK_DELETE
                    escapeSequence = "\033[3~";
                    break;
                case 0x21: // VK_PRIOR PageUp
                case 0x22: // VK_NEXT PageDown
                case 0x26: // VK_UP
                case 0x28: // VK_DOWN
                case 0x2D: // VK_INSERT

                case 0x70: // VK_F1
                case 0x71: // VK_F2
                case 0x72: // VK_F3
                case 0x73: // VK_F4
                case 0x74: // VK_F5
                case 0x75: // VK_F6
                case 0x76: // VK_F7
                case 0x77: // VK_F8
                case 0x78: // VK_F9
                case 0x79: // VK_F10
                case 0x7A: // VK_F11
                case 0x7B: // VK_F12
                    return "";
                case 0x5D: // VK_CLOSE_BRACKET(Menu key)
                case 0x5B: // VK_OPEN_BRACKET(Window key)
                default:
                    return null;
            }
            if (keyState != 0) {
                //with modifiers - ignore:
                return "";
            }
            return escapeSequence;
        }
    }

}
