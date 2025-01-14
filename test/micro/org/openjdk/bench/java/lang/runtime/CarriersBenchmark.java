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

package org.openjdk.bench.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.runtime.ArrayCarriers;
import java.lang.runtime.Carriers;
import java.lang.runtime.MethodHandleCarriers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@State(Scope.Benchmark)
@Fork(1)
public class CarriersBenchmark {

    private static final MethodHandle carriersConstructor;
    private static final MethodHandle carriersComponent0;
    private static final MethodHandle carriersComponent1;
    private static final MethodHandle arrayCarriersConstructor;
    private static final MethodHandle arrayCarriersComponent0;
    private static final MethodHandle arrayCarriersComponent1;
    private static final MethodHandle methodHandleConstructor;
    private static final MethodHandle methodHandleComponent0;
    private static final MethodHandle methodHandleComponent1;

    private static final MethodHandle carriersConstructor_2;
    private static final MethodHandle carriersComponent0_2;
    private static final MethodHandle carriersComponent1_2;
    private static final MethodHandle carriersComponent2_2;
    private static final MethodHandle carriersComponent3_2;
    private static final MethodHandle carriersComponent4_2;
    private static final MethodHandle carriersComponent5_2;
    private static final MethodHandle arrayCarriersConstructor_2;
    private static final MethodHandle arrayCarriersComponent0_2;
    private static final MethodHandle arrayCarriersComponent1_2;
    private static final MethodHandle arrayCarriersComponent2_2;
    private static final MethodHandle arrayCarriersComponent3_2;
    private static final MethodHandle arrayCarriersComponent4_2;
    private static final MethodHandle arrayCarriersComponent5_2;
    private static final MethodHandle methodHandleCarriersConstructor_2;
    private static final MethodHandle methodHandleCarriersComponent0_2;
    private static final MethodHandle methodHandleCarriersComponent1_2;
    private static final MethodHandle methodHandleCarriersComponent2_2;
    private static final MethodHandle methodHandleCarriersComponent3_2;
    private static final MethodHandle methodHandleCarriersComponent4_2;
    private static final MethodHandle methodHandleCarriersComponent5_2;

    static {
        MethodType dataType = MethodType.methodType(Object.class, int.class, String.class);
        carriersConstructor = Carriers.initializingConstructor(dataType);
        carriersComponent0 = Carriers.component(dataType, 0);
        carriersComponent1 = Carriers.component(dataType, 1);
        arrayCarriersConstructor = ArrayCarriers.initializingConstructor(dataType);
        arrayCarriersComponent0 = ArrayCarriers.component(dataType, 0);
        arrayCarriersComponent1 = ArrayCarriers.component(dataType, 1);
        methodHandleConstructor = MethodHandleCarriers.initializingConstructor(dataType);
        methodHandleComponent0 = MethodHandleCarriers.component(dataType, 0);
        methodHandleComponent1 = MethodHandleCarriers.component(dataType, 1);

        MethodType dataType_2 = MethodType.methodType(Object.class, boolean.class, int.class, long.class, float.class, double.class, String.class);
        carriersConstructor_2 = Carriers.initializingConstructor(dataType_2);
        carriersComponent0_2 = Carriers.component(dataType_2, 0);
        carriersComponent1_2 = Carriers.component(dataType_2, 1);
        carriersComponent2_2 = Carriers.component(dataType_2, 2);
        carriersComponent3_2 = Carriers.component(dataType_2, 3);
        carriersComponent4_2 = Carriers.component(dataType_2, 4);
        carriersComponent5_2 = Carriers.component(dataType_2, 5);
        arrayCarriersConstructor_2 = ArrayCarriers.initializingConstructor(dataType_2);
        arrayCarriersComponent0_2 = ArrayCarriers.component(dataType_2, 0);
        arrayCarriersComponent1_2 = ArrayCarriers.component(dataType_2, 1);
        arrayCarriersComponent2_2 = ArrayCarriers.component(dataType_2, 2);
        arrayCarriersComponent3_2 = ArrayCarriers.component(dataType_2, 3);
        arrayCarriersComponent4_2 = ArrayCarriers.component(dataType_2, 4);
        arrayCarriersComponent5_2 = ArrayCarriers.component(dataType_2, 5);
        methodHandleCarriersConstructor_2 = MethodHandleCarriers.initializingConstructor(dataType_2);
        methodHandleCarriersComponent0_2 = MethodHandleCarriers.component(dataType_2, 0);
        methodHandleCarriersComponent1_2 = MethodHandleCarriers.component(dataType_2, 1);
        methodHandleCarriersComponent2_2 = MethodHandleCarriers.component(dataType_2, 2);
        methodHandleCarriersComponent3_2 = MethodHandleCarriers.component(dataType_2, 3);
        methodHandleCarriersComponent4_2 = MethodHandleCarriers.component(dataType_2, 4);
        methodHandleCarriersComponent5_2 = MethodHandleCarriers.component(dataType_2, 5);
    }

    private final Data[] data = new Data[] {
        new Data(0, "name0"),
        new Data(1, "name1"),
        new Data(2, "name2"),
        new Data(1024, "name2"),
        new Data(Integer.MIN_VALUE, "name2"),
        new Data(Integer.MAX_VALUE, "name2")
    };

    @Benchmark
    public void carriers(Blackhole b) throws Throwable {
        for (Data d : data) {
            Object carrier = carriersConstructor.invoke(d.i(), d.s());
            b.consume((int) carriersComponent0.invoke(carrier));
            b.consume((String) carriersComponent1.invoke(carrier));
        }
    }

    @Benchmark
    public void arrayCarriers(Blackhole b) throws Throwable {
        for (Data d : data) {
            Object carrier = arrayCarriersConstructor.invoke(d.i(), d.s());
            b.consume((int) arrayCarriersComponent0.invoke(carrier));
            b.consume((String) arrayCarriersComponent1.invoke(carrier));
        }
    }

    @Benchmark
    public void methodHandleCarriers(Blackhole b) throws Throwable {
        for (Data d : data) {
            Object carrier = methodHandleConstructor.invoke(d.i(), d.s());
            b.consume((int) methodHandleComponent0.invoke(carrier));
            b.consume((String) methodHandleComponent1.invoke(carrier));
        }
    }

    private final Data_2[] data_2 = new Data_2[] {
        new Data_2(true, 0, 1, 2, 3, "name0"),
        new Data_2(true, Integer.MAX_VALUE, 1, 2, 3, "name0"),
        new Data_2(true, Integer.MAX_VALUE, Long.MIN_VALUE, 2, 3, "name0"),
        new Data_2(true, Integer.MAX_VALUE, Long.MIN_VALUE, Float.MAX_VALUE, Double.MIN_VALUE, "name0"),
    };

    @Benchmark
    public void carriers_2(Blackhole b) throws Throwable {
        for (Data_2 d : data_2) {
            Object carrier = carriersConstructor_2.invoke(d.b(), d.i(), d.l(), d.f(), d.d(), d.s());
            b.consume((boolean) carriersComponent0_2.invoke(carrier));
            b.consume((int) carriersComponent1_2.invoke(carrier));
            b.consume((long) carriersComponent2_2.invoke(carrier));
            b.consume((float) carriersComponent3_2.invoke(carrier));
            b.consume((double) carriersComponent4_2.invoke(carrier));
            b.consume((String) carriersComponent5_2.invoke(carrier));
        }
    }

    @Benchmark
    public void arrayCarriers_2(Blackhole b) throws Throwable {
        for (Data_2 d : data_2) {
            Object carrier = arrayCarriersConstructor_2.invoke(d.b(), d.i(), d.l(), d.f(), d.d(), d.s());
            b.consume((boolean) arrayCarriersComponent0_2.invoke(carrier));
            b.consume((int) arrayCarriersComponent1_2.invoke(carrier));
            b.consume((long) arrayCarriersComponent2_2.invoke(carrier));
            b.consume((float) arrayCarriersComponent3_2.invoke(carrier));
            b.consume((double) arrayCarriersComponent4_2.invoke(carrier));
            b.consume((String) arrayCarriersComponent5_2.invoke(carrier));
        }
    }

    @Benchmark
    public void methodHandleCarriers_2(Blackhole b) throws Throwable {
        for (Data_2 d : data_2) {
            Object carrier = methodHandleCarriersConstructor_2.invoke(d.b(), d.i(), d.l(), d.f(), d.d(), d.s());
            b.consume((boolean) methodHandleCarriersComponent0_2.invoke(carrier));
            b.consume((int) methodHandleCarriersComponent1_2.invoke(carrier));
            b.consume((long) methodHandleCarriersComponent2_2.invoke(carrier));
            b.consume((float) methodHandleCarriersComponent3_2.invoke(carrier));
            b.consume((double) methodHandleCarriersComponent4_2.invoke(carrier));
            b.consume((String) methodHandleCarriersComponent5_2.invoke(carrier));
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CarriersBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }

    record Data(int i, String s) {}
    record Data_2(boolean b, int i, long l, float f, double d, String s) {}
}
