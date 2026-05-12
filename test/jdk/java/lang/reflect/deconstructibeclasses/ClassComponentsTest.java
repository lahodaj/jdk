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

/*
 * @test
 * @bug 9999999
 * @summary XXX
 * @compile ClassComponentsTest.java
 * @run junit/othervm ClassComponentsTest
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ClassComponentsTest {

    interface I(@A int c1, @A String c2, List<@TA String> c3) {}

    @Test
    public void testGetClassComponent() {
        List<String> actual =
            Arrays.stream(I.class.getClassComponents())
                  .map(cc -> String.valueOf(Arrays.stream(cc.getAnnotations()).map(ann -> ann.toString()).collect(Collectors.joining(" ", "", " ")) + cc.getAnnotatedType()) + " " + cc.getName())
                  .toList();
        List<String> expected = List.of(
            "@ClassComponentsTest.A() int c1",
            "@ClassComponentsTest.A() java.lang.String c2",
            //TODO: missing type annotation! (missing in the classfile):
            " java.util.List<java.lang.String> c3"
        );
        assertEquals(expected, actual);
        assertNull(ClassComponentsTest.class.getClassComponents());
    }

    @Test
    public void testIsDeconstructible() {
        assertTrue(I.class.isDeconstructible());
        assertFalse(ClassComponentsTest.class.isDeconstructible());
    }

    @Test
    public void testGetDeconstructionShape() {
        assertArrayEquals(new Object[] {int.class, String.class, List.class}, I.class.getDeconstructionShape());
        assertNull(ClassComponentsTest.class.getDeconstructionShape());
    }

    @Test
    public void testDeconstruct() {
        record R(int c1, String c2, List<String> c3) implements I {}
        Object o = new R(-1, "a", List.of("b"));
        assertArrayEquals(new Object[] {-1, "a", List.of("b")}, I.class.deconstruct(o));
        assertThrows(IllegalArgumentException.class, () -> {
            ClassComponentsTest.class.deconstruct(new ClassComponentsTest());
        });
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.CLASS_COMPONENT)
    @interface A {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface TA {}
}
