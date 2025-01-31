/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.console;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntSupplier;

public class SimpleConsoleReader {

    //public, to simplify access from tests:
    public static char[] doRead(Reader reader, Writer out, boolean password, int firstLineOffset, IntSupplier terminalWidthSupplier) throws IOException {
        CleanableBuffer result = new CleanableBuffer();
        try {
            doReadImpl(reader, out, password, firstLineOffset, terminalWidthSupplier, result);
            return Arrays.copyOf(result.data, result.length);
        } finally {
            result.zeroOut();
        }
    }

    private static void doReadImpl(Reader reader, Writer out, boolean password, int firstLineOffset, IntSupplier terminalWidthSupplier, CleanableBuffer result) throws IOException {
        int caret = 0;
        int r;
        PaintState prevState = new PaintState();

        READ: while (true) {
            //paint:
            if (firstLineOffset != (-1) && !password) {
                prevState = repaint(out, firstLineOffset, terminalWidthSupplier, result.data, result.length, caret, prevState);
            }

            //read
            r = reader.read();
            switch (r) {
                case -1: continue READ;
                case '\r': break READ;
                case 4: break READ; //EOF/Ctrl-D
                case 127:
                    //backspace:
                    if (caret > 0) {
                        result.delete(caret - 1, caret);
                        caret--;
                    }
                    continue READ;
                case '\033':
                    r = reader.read();
                    switch (r) {
                        case '[':
                            r = reader.read();

                            StringBuilder firstNumber = new StringBuilder();

                            r = readNumber(reader, r, firstNumber);

                            String modifier;
                            String key;

                            switch (r) {
                                case '~' -> {
                                    key = firstNumber.toString();
                                    modifier = "1";
                                }
                                case ';' -> {
                                    key = firstNumber.toString();

                                    StringBuilder modifierBuilder = new StringBuilder();

                                    r = readNumber(reader, reader.read(), modifierBuilder);
                                    modifier = modifierBuilder.toString();

                                    if (r == 'R') {
                                        firstLineOffset = Integer.parseInt(modifier) - 1;
                                        continue READ;
                                    }

                                    if (r != '~') {
                                        //TODO: unexpected, anything that can be done?
                                    }
                                }
                                default -> {
                                    key = Character.toString(r);
                                    modifier = firstNumber.isEmpty() ? "1"
                                                                     : firstNumber.toString();
                                }
                            }

                            if ("1".equals(modifier)) {
                                switch (key) {
                                    case "C": if (caret < result.length()) caret++; break;
                                    case "D": if (caret > 0) caret--; break;
                                    case "1", "H": caret = 0; break;
                                    case "4", "F": caret = result.length(); break;
                                    case "3":
                                        //delete
                                        result.delete(caret, caret + 1);
                                        continue READ;
                                }
                            }
                    }
                    continue READ;
            }

            result.insert(caret, (char) r);
            caret++;
        }

        if (!password) {
            //show the final state:
            repaint(out, firstLineOffset, terminalWidthSupplier, result.data, result.length, caret, prevState);
        }

        out.append("\n\r").flush();
    }

    private static PaintState repaint(Writer out, int firstLineOffset, IntSupplier terminalWidthSupplier, char[] toDisplay, int toDisplayLength, int caret, PaintState prevPaintState) throws IOException {
        //TODO: compute smaller (ideally minimal) changes, and apply them instead of repainting everything:
        record DisplayLine(int lineStartIndex, int lineLength) {}
        int terminalWidth = terminalWidthSupplier.getAsInt();
        List<DisplayLine> toDisplayLines = new ArrayList<>();
        int lineOffset = firstLineOffset;
        int lineStartIndex = 0;

        while (lineStartIndex < toDisplayLength) {
            int currentLineColumns = terminalWidth - lineOffset;
            int maxCurrentLineLen = Math.min(currentLineColumns, toDisplayLength - lineStartIndex);
            toDisplayLines.add(new DisplayLine(lineStartIndex, maxCurrentLineLen));
            lineStartIndex += maxCurrentLineLen;
            lineOffset = 0;
        }

        for (int i = prevPaintState.caretLine() + 1; i < prevPaintState.lines(); i++) {
            out.append("\033[B");
        }
        for (int i = prevPaintState.lines() - 1; i >= 0; i--) {
            if (i == 0) {
                out.append("\033[" + (firstLineOffset + 1) + "G");
                out.append("\033[K");
            } else {
                out.append("\r");
                out.append("\033[K");
                out.append("\033[A");
            }
        }
        for (Iterator<DisplayLine> it = toDisplayLines.iterator(); it.hasNext();) {
            DisplayLine line = it.next();
            out.write(toDisplay, line.lineStartIndex(), line.lineLength());
            if (it.hasNext()) {
                out.append("\n\r");
            }
        }

        int prevCaretLine = prevPaintState.lines();

        if (caret < toDisplayLength) {
            int currentPos = toDisplayLength;

            prevCaretLine = prevPaintState.lines() - 1;

            while (caret < currentPos - toDisplayLines.get(prevCaretLine).lineLength()) {
                out.append("\033[A");
                currentPos -= toDisplayLines.get(prevCaretLine).lineLength();
                prevCaretLine--;
            }

            int currentLineStart = currentPos - toDisplayLines.get(prevCaretLine).lineLength();
            int linePosition = caret - currentLineStart;

            if (prevCaretLine == 0) {
                linePosition += firstLineOffset;
            }

            out.append("\033[" + (linePosition + 1) + "G");
        }
        out.flush();
        return new PaintState(toDisplayLines.size(), prevCaretLine);
    }

    private static int readNumber(Reader reader, int r, StringBuilder number) throws IOException {
        while (Character.isDigit(r)) {
            number.append((char) r);
            r = reader.read();
        }
        return r;
    }

    public record Size(int width, int height) {}
    record PaintState(int lines, int caretLine) {

        public PaintState() {
            this(1, 0);
        }

    }

    private static final class CleanableBuffer {
        private char[] data = new char[16];
        private int length;

        public void delete(int from, int to) {
            System.arraycopy(data, to, data, from, length - to);
            length--;
        }

        public int length() {
            return length;
        }

        public void insert(int caret, char c) {
            while (length + 1 >= data.length) {
                char[] newData = Arrays.copyOf(data, data.length * 2);

                zeroOut();
                data = newData;
            }

            System.arraycopy(data, caret, data, caret + 1, length - caret);
            data[caret] = c;
            length++;
        }

        public void zeroOut() {
            Arrays.fill(data, '\0');
        }
    }
}
