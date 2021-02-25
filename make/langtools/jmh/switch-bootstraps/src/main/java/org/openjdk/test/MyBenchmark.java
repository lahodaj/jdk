/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.openjdk.test;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;


@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class MyBenchmark {

//    @Param({"A", "B", "C", "O"})
//    public E switchOn;
//
//    @Benchmark
//    public int testMethod() {
//        int res;
//        switch (switchOn) {
//            case A -> res = 0;
//            case B -> res = 1;
//            case C -> res = 2;
//            default -> res = 3;
//        }
//        return res;
//    }
//
//    @Benchmark
//    public int noSwitchBootstraps() {
//        int res;
//        switch (switchOn) {
//            case A -> res = 0;
//            case B -> res = 1;
//            case C -> res = 2;
//            default -> res = 3;
//        }
//        return res;
//    }
//
//    public enum E {
//       A, B, C, O; 
//    }

    @Param({"a", "b", "c", "o"})
    public String switchOn;

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_hashMap() {
        int res;
        switch (switchOn) {
            case "a" -> res = 0;
            case "b" -> res = 1;
            case "c" -> res = 2;
            default -> res = 3;
        }
        return res;
    }

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_binarySearch() {
        int res;
        switch (switchOn) {
            case "a" -> res = 0;
            case "b" -> res = 1;
            case "c" -> res = 2;
            default -> res = 3;
        }
        return res;
    }

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_ifTree() {
        int res;
        switch (switchOn) {
            case "a" -> res = 0;
            case "b" -> res = 1;
            case "c" -> res = 2;
            default -> res = 3;
        }
        return res;
    }

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_legacy() {
        int res;
        switch (switchOn) {
            case "a" -> res = 0;
            case "b" -> res = 1;
            case "c" -> res = 2;
            default -> res = 3;
        }
        return res;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MyBenchmark.class.getSimpleName())
//                .param("arg", "41", "42") // Use this to selectively constrain/override parameters
                .build();

        new Runner(opt).run();
    }
}
