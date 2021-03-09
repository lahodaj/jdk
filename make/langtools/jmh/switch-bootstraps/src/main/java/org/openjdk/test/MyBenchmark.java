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


@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(5)
//@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
//@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
//@Fork(1)
@State(Scope.Benchmark)
public class MyBenchmark {

//    @Param({"A", "B", "C", "O"})
//    public E switchOn;
//
//    @Benchmark
//    public int testMethod() {
//        int res;
//        switch (switchOn) {
//            case A -> res |= 0;
//            case B -> res |= 1;
//            case C -> res |= 2;
//            default -> res |= 3;
//        }
//        return res;
//    }
//
//    @Benchmark
//    public int noSwitchBootstraps() {
//        int res;
//        switch (switchOn) {
//            case A -> res |= 0;
//            case B -> res |= 1;
//            case C -> res |= 2;
//            default -> res |= 3;
//        }
//        return res;
//    }
//
//    public enum E {
//       A, B, C, O; 
//    }

    @Param({"SnippetCounters UseTypeCheckHints ImmutableCode SmallCompiledLowLevelGraphSize HotSpotPrintInlining RemoveNeverExecutedCode OmitHotExceptionStacktrace LoopMaxUnswitch TraceEscapeAnalysis TraceInliningForStubsAndSnippets GenLoopSafepoints OptImplicitNullChecks StringIndexOfLimit SupportJsrBytecodes MaximumEscapeAnalysisArrayLength InlineMegamorphicCalls LoopUnswitch InlineVTableStubs GCDebugStartCycle ReadEliminationMaxLoopVisits ReassociateInvariants InlineEverything RawConditionalElimination ReplaceInputsWithConstantsBasedOnStamps CanOmitFrame DeoptALot PartialEscapeAnalysis LoopHeaderAlignment StressInvokeWithExceptionNode EagerSnippets MaximumRecursiveInlining EscapeAnalysisIterations ZapStackOnMethodEntry RegisterPressure OptAssumptions ConditionalEliminationMaxIterations TrivialInliningSize Intrinsify UseGraalStubs TraceInlining DeoptsToDisableOptimisticOptimization UseExceptionProbability OptConvertDeoptsToGuards MatchExpressions LimitInlinedInvokes MinimumPeelFrequency StressTestEarlyReads GuardPriorities LoopPeeling OptScheduleOutOfLoops AOTVerifyOops TrackNodeSourcePosition UseEncodedGraphs OptFloatingReads PartialUnroll MinimalBulkZeroingSize VerifyHeapAtReturn EscapeAnalyzeOnly StressExplicitExceptionCode ConditionalElimination MaximumInliningSize OptDevirtualizeInvokesOptimistically FullUnroll UseLoopLimitChecks InlinePolymorphicCalls OptEliminateGuards EscapeAnalysisLoopCutoff InlineMonomorphicCalls MaximumDesiredSize VerifyPhases UseSnippetGraphCache OptDeoptimizationGrouping GeneratePIC PrintProfilingInformation MegamorphicInliningMinMethodProbability AlwaysInlineVTableStubs TrackNodeInsertion OptReadElimination",
            "OptReadElimination",
            "SnippetCounters UseTypeCheckHints",
            "other"})
    public String switchOn;

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_legacy() {
        int res = 0;
        for (String c : switchOn.split(" ")) {
        switch (c) {
            case "SnippetCounters" ->
                res |= 0;
            case "UseTypeCheckHints" ->
                res |= 1;
            case "ImmutableCode" ->
                res |= 2;
            case "SmallCompiledLowLevelGraphSize" ->
                res |= 3;
            case "HotSpotPrintInlining" ->
                res |= 4;
            case "RemoveNeverExecutedCode" ->
                res |= 5;
            case "OmitHotExceptionStacktrace" ->
                res |= 6;
            case "LoopMaxUnswitch" ->
                res |= 7;
            case "TraceEscapeAnalysis" ->
                res |= 8;
            case "TraceInliningForStubsAndSnippets" ->
                res |= 9;
            case "GenLoopSafepoints" ->
                res |= 10;
            case "OptImplicitNullChecks" ->
                res |= 11;
            case "StringIndexOfLimit" ->
                res |= 12;
            case "SupportJsrBytecodes" ->
                res |= 13;
            case "MaximumEscapeAnalysisArrayLength" ->
                res |= 14;
            case "InlineMegamorphicCalls" ->
                res |= 15;
            case "LoopUnswitch" ->
                res |= 16;
            case "InlineVTableStubs" ->
                res |= 17;
            case "GCDebugStartCycle" ->
                res |= 18;
            case "ReadEliminationMaxLoopVisits" ->
                res |= 19;
            case "ReassociateInvariants" ->
                res |= 20;
            case "InlineEverything" ->
                res |= 21;
            case "RawConditionalElimination" ->
                res |= 22;
            case "ReplaceInputsWithConstantsBasedOnStamps" ->
                res |= 23;
            case "CanOmitFrame" ->
                res |= 24;
            case "DeoptALot" ->
                res |= 25;
            case "PartialEscapeAnalysis" ->
                res |= 26;
            case "LoopHeaderAlignment" ->
                res |= 27;
            case "StressInvokeWithExceptionNode" ->
                res |= 28;
            case "EagerSnippets" ->
                res |= 29;
            case "MaximumRecursiveInlining" ->
                res |= 30;
            case "EscapeAnalysisIterations" ->
                res |= 31;
            case "ZapStackOnMethodEntry" ->
                res |= 32;
            case "RegisterPressure" ->
                res |= 33;
            case "OptAssumptions" ->
                res |= 34;
            case "ConditionalEliminationMaxIterations" ->
                res |= 35;
            case "TrivialInliningSize" ->
                res |= 36;
            case "Intrinsify" ->
                res |= 37;
            case "UseGraalStubs" ->
                res |= 38;
            case "TraceInlining" ->
                res |= 39;
            case "DeoptsToDisableOptimisticOptimization" ->
                res |= 40;
            case "UseExceptionProbability" ->
                res |= 41;
            case "OptConvertDeoptsToGuards" ->
                res |= 42;
            case "MatchExpressions" ->
                res |= 43;
            case "LimitInlinedInvokes" ->
                res |= 44;
            case "MinimumPeelFrequency" ->
                res |= 45;
            case "StressTestEarlyReads" ->
                res |= 46;
            case "GuardPriorities" ->
                res |= 47;
            case "LoopPeeling" ->
                res |= 48;
            case "OptScheduleOutOfLoops" ->
                res |= 49;
            case "AOTVerifyOops" ->
                res |= 50;
            case "TrackNodeSourcePosition" ->
                res |= 51;
            case "UseEncodedGraphs" ->
                res |= 52;
            case "OptFloatingReads" ->
                res |= 53;
            case "PartialUnroll" ->
                res |= 54;
            case "MinimalBulkZeroingSize" ->
                res |= 55;
            case "VerifyHeapAtReturn" ->
                res |= 56;
            case "EscapeAnalyzeOnly" ->
                res |= 57;
            case "StressExplicitExceptionCode" ->
                res |= 58;
            case "ConditionalElimination" ->
                res |= 59;
            case "MaximumInliningSize" ->
                res |= 60;
            case "OptDevirtualizeInvokesOptimistically" ->
                res |= 61;
            case "FullUnroll" ->
                res |= 62;
            case "UseLoopLimitChecks" ->
                res |= 63;
            case "InlinePolymorphicCalls" ->
                res |= 64;
            case "OptEliminateGuards" ->
                res |= 65;
            case "EscapeAnalysisLoopCutoff" ->
                res |= 66;
            case "InlineMonomorphicCalls" ->
                res |= 67;
            case "MaximumDesiredSize" ->
                res |= 68;
            case "VerifyPhases" ->
                res |= 69;
            case "UseSnippetGraphCache" ->
                res |= 70;
            case "OptDeoptimizationGrouping" ->
                res |= 71;
            case "GeneratePIC" ->
                res |= 72;
            case "PrintProfilingInformation" ->
                res |= 73;
            case "MegamorphicInliningMinMethodProbability" ->
                res |= 74;
            case "AlwaysInlineVTableStubs" ->
                res |= 75;
            case "TrackNodeInsertion" ->
                res |= 76;
            case "OptReadElimination" ->
                res |= 77;
            default ->
                res |= 78;
        }
        }
        return res;
    }

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_ifTree() {
        int res = 0;
        for (String c : switchOn.split(" ")) {
        switch (c) {
            case "SnippetCounters" ->
                res |= 0;
            case "UseTypeCheckHints" ->
                res |= 1;
            case "ImmutableCode" ->
                res |= 2;
            case "SmallCompiledLowLevelGraphSize" ->
                res |= 3;
            case "HotSpotPrintInlining" ->
                res |= 4;
            case "RemoveNeverExecutedCode" ->
                res |= 5;
            case "OmitHotExceptionStacktrace" ->
                res |= 6;
            case "LoopMaxUnswitch" ->
                res |= 7;
            case "TraceEscapeAnalysis" ->
                res |= 8;
            case "TraceInliningForStubsAndSnippets" ->
                res |= 9;
            case "GenLoopSafepoints" ->
                res |= 10;
            case "OptImplicitNullChecks" ->
                res |= 11;
            case "StringIndexOfLimit" ->
                res |= 12;
            case "SupportJsrBytecodes" ->
                res |= 13;
            case "MaximumEscapeAnalysisArrayLength" ->
                res |= 14;
            case "InlineMegamorphicCalls" ->
                res |= 15;
            case "LoopUnswitch" ->
                res |= 16;
            case "InlineVTableStubs" ->
                res |= 17;
            case "GCDebugStartCycle" ->
                res |= 18;
            case "ReadEliminationMaxLoopVisits" ->
                res |= 19;
            case "ReassociateInvariants" ->
                res |= 20;
            case "InlineEverything" ->
                res |= 21;
            case "RawConditionalElimination" ->
                res |= 22;
            case "ReplaceInputsWithConstantsBasedOnStamps" ->
                res |= 23;
            case "CanOmitFrame" ->
                res |= 24;
            case "DeoptALot" ->
                res |= 25;
            case "PartialEscapeAnalysis" ->
                res |= 26;
            case "LoopHeaderAlignment" ->
                res |= 27;
            case "StressInvokeWithExceptionNode" ->
                res |= 28;
            case "EagerSnippets" ->
                res |= 29;
            case "MaximumRecursiveInlining" ->
                res |= 30;
            case "EscapeAnalysisIterations" ->
                res |= 31;
            case "ZapStackOnMethodEntry" ->
                res |= 32;
            case "RegisterPressure" ->
                res |= 33;
            case "OptAssumptions" ->
                res |= 34;
            case "ConditionalEliminationMaxIterations" ->
                res |= 35;
            case "TrivialInliningSize" ->
                res |= 36;
            case "Intrinsify" ->
                res |= 37;
            case "UseGraalStubs" ->
                res |= 38;
            case "TraceInlining" ->
                res |= 39;
            case "DeoptsToDisableOptimisticOptimization" ->
                res |= 40;
            case "UseExceptionProbability" ->
                res |= 41;
            case "OptConvertDeoptsToGuards" ->
                res |= 42;
            case "MatchExpressions" ->
                res |= 43;
            case "LimitInlinedInvokes" ->
                res |= 44;
            case "MinimumPeelFrequency" ->
                res |= 45;
            case "StressTestEarlyReads" ->
                res |= 46;
            case "GuardPriorities" ->
                res |= 47;
            case "LoopPeeling" ->
                res |= 48;
            case "OptScheduleOutOfLoops" ->
                res |= 49;
            case "AOTVerifyOops" ->
                res |= 50;
            case "TrackNodeSourcePosition" ->
                res |= 51;
            case "UseEncodedGraphs" ->
                res |= 52;
            case "OptFloatingReads" ->
                res |= 53;
            case "PartialUnroll" ->
                res |= 54;
            case "MinimalBulkZeroingSize" ->
                res |= 55;
            case "VerifyHeapAtReturn" ->
                res |= 56;
            case "EscapeAnalyzeOnly" ->
                res |= 57;
            case "StressExplicitExceptionCode" ->
                res |= 58;
            case "ConditionalElimination" ->
                res |= 59;
            case "MaximumInliningSize" ->
                res |= 60;
            case "OptDevirtualizeInvokesOptimistically" ->
                res |= 61;
            case "FullUnroll" ->
                res |= 62;
            case "UseLoopLimitChecks" ->
                res |= 63;
            case "InlinePolymorphicCalls" ->
                res |= 64;
            case "OptEliminateGuards" ->
                res |= 65;
            case "EscapeAnalysisLoopCutoff" ->
                res |= 66;
            case "InlineMonomorphicCalls" ->
                res |= 67;
            case "MaximumDesiredSize" ->
                res |= 68;
            case "VerifyPhases" ->
                res |= 69;
            case "UseSnippetGraphCache" ->
                res |= 70;
            case "OptDeoptimizationGrouping" ->
                res |= 71;
            case "GeneratePIC" ->
                res |= 72;
            case "PrintProfilingInformation" ->
                res |= 73;
            case "MegamorphicInliningMinMethodProbability" ->
                res |= 74;
            case "AlwaysInlineVTableStubs" ->
                res |= 75;
            case "TrackNodeInsertion" ->
                res |= 76;
            case "OptReadElimination" ->
                res |= 77;
            default ->
                res |= 78;
        }
        }
        return res;
    }

    @Benchmark
    public int javacStringSwitchDesugaringStrategy_asmSwitch() {
        int res = 0;
        for (String c : switchOn.split(" ")) {
        switch (c) {
            case "SnippetCounters" ->
                res |= 0;
            case "UseTypeCheckHints" ->
                res |= 1;
            case "ImmutableCode" ->
                res |= 2;
            case "SmallCompiledLowLevelGraphSize" ->
                res |= 3;
            case "HotSpotPrintInlining" ->
                res |= 4;
            case "RemoveNeverExecutedCode" ->
                res |= 5;
            case "OmitHotExceptionStacktrace" ->
                res |= 6;
            case "LoopMaxUnswitch" ->
                res |= 7;
            case "TraceEscapeAnalysis" ->
                res |= 8;
            case "TraceInliningForStubsAndSnippets" ->
                res |= 9;
            case "GenLoopSafepoints" ->
                res |= 10;
            case "OptImplicitNullChecks" ->
                res |= 11;
            case "StringIndexOfLimit" ->
                res |= 12;
            case "SupportJsrBytecodes" ->
                res |= 13;
            case "MaximumEscapeAnalysisArrayLength" ->
                res |= 14;
            case "InlineMegamorphicCalls" ->
                res |= 15;
            case "LoopUnswitch" ->
                res |= 16;
            case "InlineVTableStubs" ->
                res |= 17;
            case "GCDebugStartCycle" ->
                res |= 18;
            case "ReadEliminationMaxLoopVisits" ->
                res |= 19;
            case "ReassociateInvariants" ->
                res |= 20;
            case "InlineEverything" ->
                res |= 21;
            case "RawConditionalElimination" ->
                res |= 22;
            case "ReplaceInputsWithConstantsBasedOnStamps" ->
                res |= 23;
            case "CanOmitFrame" ->
                res |= 24;
            case "DeoptALot" ->
                res |= 25;
            case "PartialEscapeAnalysis" ->
                res |= 26;
            case "LoopHeaderAlignment" ->
                res |= 27;
            case "StressInvokeWithExceptionNode" ->
                res |= 28;
            case "EagerSnippets" ->
                res |= 29;
            case "MaximumRecursiveInlining" ->
                res |= 30;
            case "EscapeAnalysisIterations" ->
                res |= 31;
            case "ZapStackOnMethodEntry" ->
                res |= 32;
            case "RegisterPressure" ->
                res |= 33;
            case "OptAssumptions" ->
                res |= 34;
            case "ConditionalEliminationMaxIterations" ->
                res |= 35;
            case "TrivialInliningSize" ->
                res |= 36;
            case "Intrinsify" ->
                res |= 37;
            case "UseGraalStubs" ->
                res |= 38;
            case "TraceInlining" ->
                res |= 39;
            case "DeoptsToDisableOptimisticOptimization" ->
                res |= 40;
            case "UseExceptionProbability" ->
                res |= 41;
            case "OptConvertDeoptsToGuards" ->
                res |= 42;
            case "MatchExpressions" ->
                res |= 43;
            case "LimitInlinedInvokes" ->
                res |= 44;
            case "MinimumPeelFrequency" ->
                res |= 45;
            case "StressTestEarlyReads" ->
                res |= 46;
            case "GuardPriorities" ->
                res |= 47;
            case "LoopPeeling" ->
                res |= 48;
            case "OptScheduleOutOfLoops" ->
                res |= 49;
            case "AOTVerifyOops" ->
                res |= 50;
            case "TrackNodeSourcePosition" ->
                res |= 51;
            case "UseEncodedGraphs" ->
                res |= 52;
            case "OptFloatingReads" ->
                res |= 53;
            case "PartialUnroll" ->
                res |= 54;
            case "MinimalBulkZeroingSize" ->
                res |= 55;
            case "VerifyHeapAtReturn" ->
                res |= 56;
            case "EscapeAnalyzeOnly" ->
                res |= 57;
            case "StressExplicitExceptionCode" ->
                res |= 58;
            case "ConditionalElimination" ->
                res |= 59;
            case "MaximumInliningSize" ->
                res |= 60;
            case "OptDevirtualizeInvokesOptimistically" ->
                res |= 61;
            case "FullUnroll" ->
                res |= 62;
            case "UseLoopLimitChecks" ->
                res |= 63;
            case "InlinePolymorphicCalls" ->
                res |= 64;
            case "OptEliminateGuards" ->
                res |= 65;
            case "EscapeAnalysisLoopCutoff" ->
                res |= 66;
            case "InlineMonomorphicCalls" ->
                res |= 67;
            case "MaximumDesiredSize" ->
                res |= 68;
            case "VerifyPhases" ->
                res |= 69;
            case "UseSnippetGraphCache" ->
                res |= 70;
            case "OptDeoptimizationGrouping" ->
                res |= 71;
            case "GeneratePIC" ->
                res |= 72;
            case "PrintProfilingInformation" ->
                res |= 73;
            case "MegamorphicInliningMinMethodProbability" ->
                res |= 74;
            case "AlwaysInlineVTableStubs" ->
                res |= 75;
            case "TrackNodeInsertion" ->
                res |= 76;
            case "OptReadElimination" ->
                res |= 77;
            default ->
                res |= 78;
        }
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
