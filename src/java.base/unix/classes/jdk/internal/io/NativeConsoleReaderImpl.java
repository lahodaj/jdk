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
package jdk.internal.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import jdk.internal.io.SimpleConsoleReader.Size;

public class NativeConsoleReaderImpl implements NativeConsoleReader {

    public static NativeConsoleReader create(Object readLock) {
        if (CLibrary.isTty(0)) {
            return new NativeConsoleReaderImpl();
        } else {
            return new BaseNativeConsoleReader(readLock);
        }
    }

    //TODO: read lock
    public char[] readline(Reader reader, Writer out, boolean zeroOut) throws IOException {
        Attributes originalAttributes = CLibrary.getAttributes(0);
        Attributes rawAttributes = new Attributes(originalAttributes);
        rawAttributes.setInputFlag(Attributes.InputFlag.BRKINT, false);
        rawAttributes.setInputFlag(Attributes.InputFlag.IGNPAR, false);
        rawAttributes.setInputFlag(Attributes.InputFlag.ICRNL, false);
        rawAttributes.setInputFlag(Attributes.InputFlag.IXON, false);
        rawAttributes.setInputFlag(Attributes.InputFlag.IXOFF, true);
        rawAttributes.setInputFlag(Attributes.InputFlag.IMAXBEL, false);
        rawAttributes.setLocalFlag(Attributes.LocalFlag.ICANON, false);
        rawAttributes.setLocalFlag(Attributes.LocalFlag.ECHO, false);
        Thread restoreConsole = new Thread(() -> {
            CLibrary.setAttributes(0, originalAttributes);
        });
        try {
            Runtime.getRuntime().addShutdownHook(restoreConsole);
            CLibrary.setAttributes(0, rawAttributes);
            Size size = CLibrary.getTerminalSize(0);
            out.append("\033[6n").flush(); //ask the terminal to provide cursor location
            return SimpleConsoleReader.doRead(reader, out, -1, () -> size.width());
        } finally {
            restoreConsole.run();
            Runtime.getRuntime().removeShutdownHook(restoreConsole);
        }
    }

}
