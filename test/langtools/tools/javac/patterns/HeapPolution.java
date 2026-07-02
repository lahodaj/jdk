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
 * @summary Check heap polution behavior
 * @compile HeapPolution.java
 * @run junit HeapPolution
 */
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HeapPolution {

    @Test
    void recordComponent1() {
        Box<String> b = (Box) new Box(0);
        Assertions.assertThrows(MatchException.class, () -> {
            if (b instanceof Box(String s));
        });
    }

    @Test
    void recordComponent2() {
        Box<Box<String>> b = (Box) new Box(0);
        Assertions.assertThrows(MatchException.class, () -> {
            if (b instanceof Box(Box(String s)));
        });
    }

    @Test
    void recordComponent3() {
        Box<String> b = (Box) new Box(0);
        Assertions.assertThrows(MatchException.class, () -> {
            if (b instanceof Box(var s));
        });
    }

    @Test
    void bindingPatternInstanceOf() {
        if (this.<String>get() instanceof String s) {
            Assertions.fail();
        }
    }

    @Test
    void bindingPatternSwitch() {
        Assertions.assertThrows(ClassCastException.class, () -> {
            switch (this.<String>get()) {
                case String s -> {}
            }
        });
    }

    <T> T get() {
        return (T) (Object) 0;
    }

    record Box<T>(T value) {}
}
