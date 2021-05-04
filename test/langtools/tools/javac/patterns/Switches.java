/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.function.Function;

/*
 * @test
 * @bug 8262891
 * @summary Check behavior of pattern switches.
 * @compile --enable-preview -source ${jdk.version} Switches.java
 * @run main/othervm --enable-preview Switches
 */
public class Switches {

    public static void main(String... args) {
        new Switches().run();
    }

    void run() {
        run(this::typeTestPatternSwitchTest);
        run(this::typeTestPatternSwitchExpressionTest);
        run(this::testBooleanSwitchExpression);
        assertFalse(testNullSwitch(null));
        assertTrue(testNullSwitch(""));
        runArrayTypeTest(this::testArrayTypeStatement);
        runArrayTypeTest(this::testArrayTypeExpression);
        runEnumTest(this::testEnumExpression1);
        runEnumTest(this::testEnumExpression2);
        runStringWithConstant(this::testStringWithConstant);
        runStringWithConstant(this::testStringWithConstantExpression);
    }

    void run(Function<Object, Integer> mapper) {
        assertEquals(2, mapper.apply("2"));
        assertEquals(3, mapper.apply("3"));
        assertEquals(8, mapper.apply(new StringBuilder("4")));
        assertEquals(2, mapper.apply(2));
        assertEquals(3, mapper.apply(3));
        assertEquals(-1, mapper.apply(2.0));
        assertEquals(-1, mapper.apply(new Object()));
        try {
            mapper.apply(null);
            throw new AssertionError("Expected a NullPointerException, but got nothing.");
        } catch (NullPointerException ex) {
            //OK
        }
    }

    void runArrayTypeTest(Function<Object, String> mapper) {
        assertEquals("arr0", mapper.apply(new int[0]));
        assertEquals("str6", mapper.apply("string"));
        assertEquals("i1", mapper.apply(1));
        assertEquals("", mapper.apply(1.0));
    }

    void runEnumTest(Function<E, String> mapper) {
        assertEquals("a", mapper.apply(E.A));
        assertEquals("b", mapper.apply(E.B));
        assertEquals("C", mapper.apply(E.C));
        assertEquals("null", mapper.apply(null));
    }

    void runStringWithConstant(Function<String, Integer> mapper) {
        assertEquals(1, mapper.apply("A"));
        assertEquals(2, mapper.apply("AA"));
        assertEquals(0, mapper.apply(""));
        assertEquals(-1, mapper.apply(null));
    }

    int typeTestPatternSwitchTest(Object o) {
        switch (o) {
            case String s: return Integer.parseInt(s.toString());
            case CharSequence s: return 2 * Integer.parseInt(s.toString());
            case Integer i: return i;
            case Object x: return -1;
        }
    }

    int typeTestPatternSwitchExpressionTest(Object o) {
        return switch (o) {
            case String s -> Integer.parseInt(s.toString());
            case CharSequence s -> { yield 2 * Integer.parseInt(s.toString()); }
            case Integer i -> i;
            case Object x -> -1;
        };
    }

    int testBooleanSwitchExpression(Object o) {
        Object x;
        if (switch (o) {
            default -> false;
        }) {
            return -3;
        } else if (switch (o) {
            case String s -> (x = s) != null;
            default -> false;
        }) {
            return Integer.parseInt(x.toString());
        } else if (switch (o) {
            case CharSequence s -> {
                x = s;
                yield true;
            }
            default -> false;
        }) {
            return 2 * Integer.parseInt(x.toString());
        }
        return typeTestPatternSwitchTest(o);
    }

    boolean testNullSwitch(Object o) {
        return switch (o) {
            case null -> false;
            default -> true;
        };
    }

    String testArrayTypeStatement(Object o) {
        String res;
        switch (o) {
            case Integer i -> res = "i" + i;
            case int[] arr -> res = "arr" + arr.length;
            case String str -> res = "str" + str.length();
            default -> res = "";
        }
        return res;
    }

    String testArrayTypeExpression(Object o) {
        return switch (o) {
            case Integer i -> "i" + i;
            case int[] arr -> "arr" + arr.length;
            case String str -> "str" + str.length();
            default -> "";
        };
    }

    int testStringWithConstant(String str) {
        switch (str) {
            case "A": return 1;
            case String s:  return s.length();
            case null: return -1;
        }
    }

    int testStringWithConstantExpression(String str) {
        return switch (str) {
            case "A" -> 1;
            case String s -> s.length();
            case null -> -1;
        };
    }

    String testEnumExpression1(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case null, E x -> String.valueOf(x);
        };
    }

    String testEnumExpression2(E e) {
        return switch (e) {
            case A -> "a";
            case B -> "b";
            case E x, null -> String.valueOf(x);
        };
    }

    void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    void assertEquals(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("Expected: true, but got false");
        }
    }

    void assertFalse(boolean actual) {
        if (actual) {
            throw new AssertionError("Expected: false, but got true");
        }
    }

    public enum E {
        A, B, C;
    }
}
