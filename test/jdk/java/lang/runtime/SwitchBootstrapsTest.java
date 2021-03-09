/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.SwitchBootstraps;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @test
 * @run testng SwitchBootstrapsTest
 */
@Test
public class SwitchBootstrapsTest {
    public static final MethodHandle BSM_STRING_SWITCH;
//    public static final MethodHandle BSM_ENUM_SWITCH;

    static {
        try {
            BSM_STRING_SWITCH = MethodHandles.lookup().findStatic(SwitchBootstraps.class, "stringSwitch",
                                                                  MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, String[].class));
//            BSM_ENUM_SWITCH = MethodHandles.lookup().findStatic(SwitchBootstraps.class, "enumSwitch",
//                                                                MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, Class.class, String[].class));
        }
        catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private MethodType switchType(Class<?> target) {
        return MethodType.methodType(int.class, target);
    }

    private void testString(String... targets) throws Throwable {
        MethodHandle indy = ((CallSite) BSM_STRING_SWITCH.invoke(MethodHandles.lookup(), "", switchType(String.class), targets)).dynamicInvoker();
        List<String> targetList = Stream.of(targets)
                                        .collect(Collectors.toList());

        for (int i=0; i<targets.length; i++) {
            String s = targets[i];
            int result = (int) indy.invoke(s);
            assertEquals((s == null) ? -1 : i, result);
        }

        for (String s : List.of("", "A", "AA", "AAA", "AAAA")) {
            if (!targetList.contains(s)) {
                assertEquals(targets.length, indy.invoke(s));
            }
        }
        //TODO: should the CallSite be null-tolerant? (May be slightly slower, for all switches over String.)
//        assertEquals(-1, (int) indy.invoke(null));
    }

//    private<E extends Enum<E>> void testEnum(Class<E> enumClass, String... targets) throws Throwable {
//        MethodHandle indy = ((CallSite) BSM_ENUM_SWITCH.invoke(MethodHandles.lookup(), "", switchType(Enum.class), enumClass, targets)).dynamicInvoker();
//        List<E> targetList = Stream.of(targets)
//                                   .map(s -> Enum.valueOf(enumClass, s))
//                                   .collect(Collectors.toList());
//
//        for (int i=0; i<targets.length; i++) {
//            String s = targets[i];
//            E e = Enum.valueOf(enumClass, s);
//            int result = (int) indy.invoke(e);
//            assertEquals((s == null) ? -1 : i, result);
//        }
//
//        for (E e : enumClass.getEnumConstants()) {
//            int index = (int) indy.invoke(e);
//            if (targetList.contains(e))
//                assertEquals(e.name(), targets[index]);
//            else
//                assertEquals(targets.length, index);
//        }
//
//        assertEquals(-1, (int) indy.invoke(null));
//    }

    public void testString() throws Throwable {
        testString("a", "b", "c");
        testString("c", "b", "a");
        testString("cow", "pig", "horse", "orangutan", "elephant", "dog", "frog", "ant");
        testString("a", "b", "c", "A", "B", "C");
        testString("C", "B", "A", "c", "b", "a");

        // Tests with hash collisions; Ba/CB, Ca/DB
        testString("Ba", "CB");
        testString("Ba", "CB", "Ca", "DB");

        // Test with null
        try {
            testString("a", null, "c");
            fail("expected failure");
        }
        catch (IllegalArgumentException t) {
            // success
        }
    }

//    enum E1 { A, B }
//    enum E2 { C, D, E, F, G, H }
//
//    public void testEnum() throws Throwable {
//        testEnum(E1.class);
//        testEnum(E1.class, "A");
//        testEnum(E1.class, "A", "B");
//        testEnum(E1.class, "B", "A");
//        testEnum(E2.class, "C");
//        testEnum(E2.class, "C", "D", "E", "F", "H");
//        testEnum(E2.class, "H", "C", "G", "D", "F", "E");
//
//        // Bad enum class
//        try {
//            testEnum((Class) String.class, "A");
//            fail("expected failure");
//        }
//        catch (IllegalArgumentException t) {
//            // success
//        }
//
//        // Bad enum constants
//        try {
//            testEnum(E1.class, "B", "A", "FILE_NOT_FOUND");
//            fail("expected failure");
//        }
//        catch (IllegalArgumentException t) {
//            // success
//        }
//
//        // Null enum constant
//        try {
//            testEnum(E1.class, "A", null, "B");
//            fail("expected failure");
//        }
//        catch (IllegalArgumentException t) {
//            // success
//        }
//    }

}
