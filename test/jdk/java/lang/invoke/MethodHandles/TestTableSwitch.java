/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestTableSwitch
 */

import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static org.testng.Assert.assertEquals;

public class TestTableSwitch {

    @Test
    public void testTableSwitch() throws Throwable {
        MethodHandle mhSwitch = MethodHandles.tableSwitch(
            /* default: */ testCase("Default"),
            /* case 0: */  testCase("Case 1"),
            /* case 1: */  testCase("Case 2"),
            /* case 2: */  testCase("Case 3")
        );

        assertEquals((String) mhSwitch.invokeExact((int) -1), "Default");
        assertEquals((String) mhSwitch.invokeExact((int) 0), "Case 1");
        assertEquals((String) mhSwitch.invokeExact((int) 1), "Case 2");
        assertEquals((String) mhSwitch.invokeExact((int) 2), "Case 3");
        assertEquals((String) mhSwitch.invokeExact((int) 3), "Default");
    }

    static MethodHandle testCase(String message) {
        return MethodHandles.dropArguments(MethodHandles.constant(String.class, message), 0, int.class);
    }

}
