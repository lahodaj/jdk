/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 9999999
 * @summary XXX
 * @compile -doe CaseThrows.java
 * @run main CaseThrows
 */

import java.util.Objects;
import java.util.function.Supplier;

//TODO: no case throws null/constant
//TODO: caught
public class CaseThrows {
    public static void main(String[] args) throws Throwable {
        new CaseThrows().run();
    }

    public void run() {
        assertEquals(1, statementTest());
        assertEquals(1, statementTestUncaught());
        assertEquals("a", statementTestControlFlow(() -> 0));
        assertEquals("b", statementTestControlFlow(() -> throwException(new RuntimeException())));
        assertEquals(1, expressionTest());
        assertEquals(1, expressionTestUncaught());
    }

    private int statementTest() {
        switch (throwException(new RuntimeException())) {
            default -> { return 0; }
            case throws RuntimeException ex -> { return 1; }
        }
    }

    private int statementTestUncaught() {
        try {
            switch (throwException(new InternalError())) {
                default -> { return 0; }
                case throws RuntimeException ex -> { return -1; }
            }
        } catch (InternalError e) {
            return 1;
        }
    }

    private String statementTestControlFlow(Supplier<Integer> selector) {
        String result = "";
        switch (selector.get()) {
            default -> result += "a";
            case throws RuntimeException ex -> result += "b";
        }
        return result;
    }

    private int expressionTest() {
        return switch (throwException(new RuntimeException())) {
            default -> 0;
            case throws RuntimeException ex -> 1;
        };
    }

    private int expressionTestUncaught() {
        try {
            return switch (throwException(new InternalError())) {
                default -> 0;
                case throws RuntimeException ex -> -1;
            };
        } catch (InternalError e) {
            return 1;
        }
    }

    private static <T extends Throwable> int throwException(T t) throws T {
        throw t;
    }

    void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }
}
