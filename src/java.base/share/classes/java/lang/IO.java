/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import jdk.internal.javac.PreviewFeature;

/**
 * A collection of static convenience methods that provide methods for
 * basic input/output.
 *
 * <p> Output from methods in this class use the conversion from
 * characters to bytes used by {@link System#out}, i.e.
 * {@link System##stdout.encoding stdoutencoding} by default.
 *
 * <p> Input from methods in this class uses the character set of
 * the system console as specified by {@link Console#charset}, if {@code System.console()}
 * returns a non-{@code null} value, {@code Charset.defaultCharset()} otherwise.
 *
 * @since 23
 */
@PreviewFeature(feature = PreviewFeature.Feature.IMPLICIT_CLASSES)
public final class IO {

    private static final Object readLock = new Object();
    private static BufferedReader input;

    private IO() {
        
        throw new Error("no instances");
    }

    /**
     * Writes a string representation of the specified object to the system
     * output, terminates the line and then flushes that output.
     *
     * <p> The effect of this method is the same as for the following code:
     * {@snippet lang="java":
     *     System.out.println(obj);
     *     System.out.flush();
     * }
     *
     * @param obj the object to print, may be {@code null}
     */
    public static void println(Object obj) {
        System.out.println(obj);
        System.out.flush();
    }

    /**
     * Terminates the current line on the system output and then flushes
     * that output.
     *
     * <p> The effect of this method is the same as for the following code:
     * {@snippet lang="java":
     *     System.out.println();
     *     System.out.flush();
     * }
     *
     * @since 24
     */
    public static void println() {
        println("");
    }

    /**
     * Writes a string representation of the specified object to the system
     * output and then flushes that output.
     *
     * <p> The effect of this method is the same as for the following code:
     * {@snippet lang="java":
     *     System.out.print(obj);
     *     System.out.flush();
     * }
     *
     *
     * @param obj the object to print, may be {@code null}
     */
    public static void print(Object obj) {
        System.out.print(obj);
        System.out.flush();
    }

    /**
     * Writes a prompt as if by calling {@code print}, then reads a single line
     * of text from the system input.
     *
     * <p> Once this method is used, only methods in this class should be used
     * to read from the input. Other methods (namely {@code System.in.read()} and
     * {@code Console} give undefined results;
     *
     * @param prompt the prompt string, may be {@code null}
     *
     * @return a string containing the line read from the system console, not
     * including any line-termination characters. Returns {@code null} if an
     * end of stream has been reached without having read any characters.
     *
     * @throws IOError if an I/O error occurs
     */
    public static String readln(String prompt) throws IOError {
        print(prompt);
        synchronized (readLock) {
            if (input == null) {
                Console console = System.console();
                Charset charset = console != null ? console.charset()
                                                  : Charset.defaultCharset();

                input = new BufferedReader(new InputStreamReader(System.in, charset));
            }
            try {
                return input.readLine();
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }
    }

    /**
     * Reads a single line of text from the system input.
     *
     * <p> Once this method is used, only methods in this class should be used
     * to read from the input. Other methods (namely {@code System.in.read()} and
     * {@code Console} give undefined results;
     *
     * @return a string containing the line read from the system console, not
     * including any line-termination characters. Returns {@code null} if an
     * end of stream has been reached without having read any characters.
     *
     * @throws IOError if an I/O error occurs
     *
     * @since 24
     */
    public static String readln() throws IOError {
        return readln("");
    }

}
