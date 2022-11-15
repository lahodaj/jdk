/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell.execution.impl;

import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import jdk.internal.io.JdkConsole;
import jdk.internal.io.JdkConsoleProvider;
import jdk.jshell.JShellConsole;

/**
 *
 */
public class ConsoleProviderImpl extends JdkConsoleProvider {

    public ConsoleProviderImpl() {
    }

    private static InputStream remoteOutput;
    private static OutputStream remoteInput;

    @Override
    public JdkConsole console(Charset charset, boolean isTTY) {
        if (remoteOutput != null && remoteInput != null) {
            return new RemoteConsole(remoteOutput, remoteInput);
        }
        return null;
    }

    public static void setRemoteOutput(InputStream remoteOutput) {
        ConsoleProviderImpl.remoteOutput = remoteOutput;
    }

    public static void setRemoteInput(OutputStream remoteInput) {
        ConsoleProviderImpl.remoteInput = remoteInput;
    }

    private static void sendChars(OutputStream remoteInput, char[] data, int off, int len) throws IOException {
        sendInt(remoteInput, len);
        for (int i = 0; i < len; i++) {
            char c = data[off + i];

            remoteInput.write((c >> 8) & 0xFF);
            remoteInput.write((c >> 0) & 0xFF);
        }
    }

    private static void sendInt(OutputStream remoteInput, int data) throws IOException {
        remoteInput.write((data >> 24) & 0xFF);
        remoteInput.write((data >> 16) & 0xFF);
        remoteInput.write((data >>  8) & 0xFF);
        remoteInput.write((data >>  0) & 0xFF);
    }

    private static final class RemoteConsole extends JdkConsole {
        private InputStream remoteOutput;
        private OutputStream remoteInput;

        private void sendChars(char[] data, int off, int len) throws IOException {
            ConsoleProviderImpl.sendChars(remoteInput, data, off, len);
        }

        private int readChars(char[] data, int off, int len) throws IOException {
            sendInt(len);
            int actualLen = readInt();
            for (int i = 0; i < actualLen; i++) {
                data[off + i] = (char) ((remoteOutput.read() <<  8) |
                                        (remoteOutput.read() <<  0));
            }
            return actualLen;
        }

        private char[] readChars() throws IOException {
            int actualLen = readInt();
            char[] result = new char[actualLen];
            for (int i = 0; i < actualLen; i++) {
                result[i] = (char) ((remoteOutput.read() <<  8) |
                                    (remoteOutput.read() <<  0));
            }
            return result;
        }

        private void sendInt(int data) throws IOException {
            ConsoleProviderImpl.sendInt(remoteInput, data);
        }

        private int readInt() throws IOException {
            return (remoteOutput.read() << 24) |
                   (remoteOutput.read() << 16) |
                   (remoteOutput.read() <<  8) |
                   (remoteOutput.read() <<  0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PrintWriter writer() {
            return new PrintWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    remoteInput.write(Task.WRITE_CHARS.ordinal());
                    sendChars(cbuf, off, len);
                    remoteOutput.read();
                }

                @Override
                public void flush() throws IOException {
                    RemoteConsole.this.flush();
                }

                @Override
                public void close() throws IOException {
                }
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Reader reader() {
            return new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    remoteInput.write(Task.READ_CHARS.ordinal());
                    return readChars(cbuf, off, len);
                }

                @Override
                public void close() throws IOException {
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole format(String fmt, Object ...args) {
            writer().format(fmt, args).flush();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole printf(String format, Object ... args) {
            return format(format, args);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized String readLine(String fmt, Object ... args) {
            try {
                remoteInput.write(Task.READ_LINE.ordinal());
                String prompt = fmt.formatted(args);
                char[] chars = prompt.toCharArray();
                sendChars(chars, 0, chars.length);
                char[] line = readChars();
                return new String(line);
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String readLine() {
            return readLine("");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized char[] readPassword(String fmt, Object ... args) {
            try {
                remoteInput.write(Task.READ_PASSWORD.ordinal());
                String prompt = fmt.formatted(args);
                char[] chars = prompt.toCharArray();
                sendChars(chars, 0, chars.length);
                return readChars();
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public char[] readPassword() {
            return readPassword("");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void flush() {
            try {
                remoteInput.write(Task.FLUSH.ordinal());
                remoteOutput.read();
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        @Override
        public Charset charset() {
            return StandardCharsets.UTF_8; //TODO: pass a "real" charset? What does it mean?
        }

        public RemoteConsole(InputStream remoteOutput, OutputStream remoteInput) {
            this.remoteOutput = remoteOutput;
            this.remoteInput = remoteInput;
        }

    }

    public static final class ConsoleOutputStream extends OutputStream {

        int[] buffer = new int[1024];
        int bp;
        final JShellConsole console;
        public final InputStream sinkInput;
        final OutputStream sinkOutput;

        public ConsoleOutputStream(JShellConsole console) {
            this.console = console;
            PipeInputStream sinkInput = new PipeInputStream();
            this.sinkInput = sinkInput;
            this.sinkOutput = sinkInput.createOutput();
        }

        @Override
        public void write(int b) throws IOException {
            if (bp + 1 >= buffer.length) {
                buffer = Arrays.copyOf(buffer, 2 * buffer.length);
            }

            buffer[bp++] = b;

            switch (Task.values()[buffer[0]]) {
                //TODO: force exhaustiveswitch:
//                case null -> throw new InternalError("impossible");
                case WRITE_CHARS -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        console.writer().write(data);
                        sinkOutput.write(0);
                        bp = 0;
                    }
                }
                case READ_CHARS -> {
                    if (bp >= 5) {
                        int len = readInt(b);
                        int c = console.reader().read();
                        //XXX: EOF handling!
                        sendChars(sinkOutput, new char[] {(char) c}, 0, 1);
                        bp = 0;
                    }
                }
                case READ_LINE -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        String line = console.readLine(new String(data));
                        char[] chars = line.toCharArray();
                        sendChars(sinkOutput, chars, 0, chars.length);
                        bp = 0;
                    }
                }
                case READ_PASSWORD -> {
                    char[] data = readCharsOrNull(1);
                    if (data != null) {
                        char[] chars = console.readPassword(new String(data));
                        sendChars(sinkOutput, chars, 0, chars.length);
                        bp = 0;
                    }
                }
                case FLUSH -> {
                    console.flush();
                    sinkOutput.write(0);
                }
            }
        }

        private int readInt(int pos) throws IOException {
            return (buffer[pos + 0] << 24) |
                   (buffer[pos + 1] << 16) |
                   (buffer[pos + 2] <<  8) |
                   (buffer[pos + 3] <<  0);
        }

        private char readChar(int pos) throws IOException {
            return (char) ((buffer[pos] << 8) |
                    (buffer[pos + 1] << 0));
        }

        private char[] readCharsOrNull(int pos) throws IOException {
            if (bp >= pos + 4) {
                int len = readInt(pos);
                if (bp >= pos + 4 + 2 * len) {
                    char[] result = new char[len];
                    for (int i = 0; i < len; i++) {
                        result[i] = readChar(pos + 4 + 2 * i);
                    }
                    return result;
                }
            }
            return null;
        }
    }

    public enum Task {
        WRITE_CHARS,
        READ_CHARS,
        READ_LINE,
        READ_PASSWORD,
        FLUSH,
        ;
    }
}
