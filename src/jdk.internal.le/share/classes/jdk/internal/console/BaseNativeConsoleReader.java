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
import java.util.Arrays;
import sun.nio.cs.StreamDecoder;

public class BaseNativeConsoleReader implements NativeConsoleReader {

    private final Object readLock;
    private char[] rcb;

    public BaseNativeConsoleReader(Object readLock) {
        this.readLock = readLock;
        this.rcb = new char[1024];
    }

    public char[] readline(Reader reader, Writer out, boolean zeroOut) throws IOException {
        reader = new LineReader(reader);
        int len = reader.read(rcb, 0, rcb.length);
        if (len < 0)
            return null;  //EOL
        if (rcb[len-1] == '\r')
            len--;        //remove CR at end;
        else if (rcb[len-1] == '\n') {
            len--;        //remove LF at end;
            if (len > 0 && rcb[len-1] == '\r')
                len--;    //remove the CR, if there is one
        }
        char[] b = new char[len];
        if (len > 0) {
            System.arraycopy(rcb, 0, b, 0, len);
            if (zeroOut) {
                Arrays.fill(rcb, 0, len, ' ');
                if (reader instanceof LineReader lr) {
                    lr.zeroOut();
                }
            }
        }
        return b;
    }

    private char[] grow() {
        assert Thread.holdsLock(readLock);
        char[] t = new char[rcb.length * 2];
        System.arraycopy(rcb, 0, t, 0, rcb.length);
        rcb = t;
        return rcb;
    }

    class LineReader extends Reader {
        private final Reader in;
        private final char[] cb;
        private int nChars, nextChar;
        boolean leftoverLF;
        LineReader(Reader in) {
            this.in = in;
            cb = new char[1024];
            nextChar = nChars = 0;
            leftoverLF = false;
        }
        public void zeroOut() throws IOException {
            if (in instanceof StreamDecoder sd) {
                sd.fillZeroToPosition();
            }
        }
        public void close () {}
        public boolean ready() throws IOException {
            //in.ready synchronizes on readLock already
            return in.ready();
        }

        public int read(char[] cbuf, int offset, int length)
                throws IOException
        {
            int off = offset;
            int end = offset + length;
            if (offset < 0 || offset > cbuf.length || length < 0 ||
                    end < 0 || end > cbuf.length) {
                throw new IndexOutOfBoundsException();
            }
            synchronized(readLock) {
                boolean eof = false;
                char c;
                for (;;) {
                    if (nextChar >= nChars) {   //fill
                        int n;
                        do {
                            n = in.read(cb, 0, cb.length);
                        } while (n == 0);
                        if (n > 0) {
                            nChars = n;
                            nextChar = 0;
                            if (n < cb.length &&
                                    cb[n-1] != '\n' && cb[n-1] != '\r') {
                                /*
                                 * we're in canonical mode so each "fill" should
                                 * come back with an eol. if there is no lf or nl at
                                 * the end of returned bytes we reached an eof.
                                 */
                                eof = true;
                            }
                        } else { /*EOF*/
                            if (off - offset == 0)
                                return -1;
                            return off - offset;
                        }
                    }
                    if (leftoverLF && cbuf == rcb && cb[nextChar] == '\n') {
                        /*
                         * if invoked by our readline, skip the leftover, otherwise
                         * return the LF.
                         */
                        nextChar++;
                    }
                    leftoverLF = false;
                    while (nextChar < nChars) {
                        c = cbuf[off++] = cb[nextChar];
                        cb[nextChar++] = 0;
                        if (c == '\n') {
                            return off - offset;
                        } else if (c == '\r') {
                            if (off == end) {
                                /* no space left even the next is LF, so return
                                 * whatever we have if the invoker is not our
                                 * readLine()
                                 */
                                if (cbuf == rcb) {
                                    cbuf = grow();
                                } else {
                                    leftoverLF = true;
                                    return off - offset;
                                }
                            }
                            if (nextChar == nChars && in.ready()) {
                                /*
                                 * we have a CR and we reached the end of
                                 * the read in buffer, fill to make sure we
                                 * don't miss a LF, if there is one, it's possible
                                 * that it got cut off during last round reading
                                 * simply because the read in buffer was full.
                                 */
                                nChars = in.read(cb, 0, cb.length);
                                nextChar = 0;
                            }
                            if (nextChar < nChars && cb[nextChar] == '\n') {
                                cbuf[off++] = '\n';
                                nextChar++;
                            }
                            return off - offset;
                        } else if (off == end) {
                            if (cbuf == rcb) {
                                cbuf = grow();
                                end = cbuf.length;
                            } else {
                                return off - offset;
                            }
                        }
                    }
                    if (eof)
                        return off - offset;
                }
            }
        }
    }

}
