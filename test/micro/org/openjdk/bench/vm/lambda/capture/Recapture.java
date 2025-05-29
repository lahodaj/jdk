/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.lambda.capture;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Measurement(time=2)
@State(Scope.Benchmark)
@Warmup(time=2)
public class Recapture {

    @Param({"0", "1", "1000", "1000000"})
    public int size;

    @Benchmark
    public int testRepeatCapture() {
        int result = 0;
        int j = size * 3;

        for (int i = 0; i < size; i++) {
            result += halfCapture(i, x -> x * j);
        }
        return result;
    }

    @Benchmark
    public int testCacheCapture() {
        int result = 0;
        int j = size * 3;
        FI $cache = null;

        for (int i = 0; i < size; i++) {
            if ($cache == null) {
                $cache = x -> x * j;
            }
            result += halfCapture(i, $cache);
        }
        return result;
    }

    @Benchmark
    public int testPreCapture() {
        int result = 0;
        int j = size * 3;
        FI $cache = x -> x * j;

        for (int i = 0; i < size; i++) {
            result += halfCapture(i, $cache);
        }
        return result;
    }

    private static int halfCapture(int i, FI fi) {
        return fi.compute(i);
    }

    interface FI {
        public int compute(int x);
    }
}
