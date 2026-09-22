/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8392648
 * @summary Verify casts to boolean and Boolean work reasonably in condition
 *          position, w.r.t. DA/DU.
 * @compile GenCondCast.java
 * @run main GenCondCast
 */

import java.util.function.ToIntFunction;

public class GenCondCast {
    static int test1(boolean b) {
        int result;
        if ((boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test2(boolean b) {
        int result;
        if ((Boolean) (b && (result = 1) > 0)) return result;
        return 0;
    }

    static int test3(boolean b) {
        if ((boolean) id(b)) return 1;
        else return 0;
    }

    static int test4(boolean b) {
        if ((Boolean) id(b)) return 1;
        else return 0;
    }

    static Object id(boolean b) {
        return b;
    }

    static void doTest(ToIntFunction<Boolean> testMethod) {
        if (testMethod.applyAsInt(true) != 1) {
            throw new IllegalStateException("Wrong value!");
        }
        if (testMethod.applyAsInt(false) != 0) {
            throw new IllegalStateException("Wrong value!");
        }
    }

    public static void main(String... args) {
        doTest(GenCondCast::test1);
        doTest(GenCondCast::test2);
        doTest(GenCondCast::test3);
        doTest(GenCondCast::test4);
    }
}
