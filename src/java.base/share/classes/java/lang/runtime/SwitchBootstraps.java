/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Bootstrap methods for linking {@code invokedynamic} call sites that implement
 * the selection functionality of the {@code switch} statement.  The bootstraps
 * take additional static arguments corresponding to the {@code case} labels
 * of the {@code switch}, implicitly numbered sequentially from {@code [0..N)}.
 *
 * <p>The bootstrap call site accepts a single parameter of the type of the
 * operand of the {@code switch}, and return an {@code int} that is the index of
 * the matched {@code case} label, {@code -1} if the target is {@code null},
 * or {@code N} if the target is not null but matches no {@code case} label.
 */
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    // Shared INIT_HOOK for all switch call sites; looks the target method up in a map
    private static final MethodHandle STRING_INIT_HOOK;
    private static final MethodHandle STRING_SWITCH_METHOD;
    private static final MethodHandle STRING_INIT_HOOK2;
    private static final MethodHandle STRING_SWITCH_METHOD2;
    private static final MethodHandle INT_EQUALS;
    private static final MethodHandle INT_GT;
    private static final MethodHandle STRING_EQUALS;
    private static final MethodHandle STRING_HASHCODE;
//    private static final MethodHandle STRING_INIT_HOOK3;
//    private static final MethodHandle STRING_SWITCH_METHOD3;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static {
        try {
            STRING_INIT_HOOK = LOOKUP.findStatic(SwitchBootstraps.class, "stringInitHook",
                                                  MethodType.methodType(MethodHandle.class, CallSite.class));
            STRING_SWITCH_METHOD = LOOKUP.findVirtual(StringSwitchCallSite.class, "doSwitch",
                                                                MethodType.methodType(int.class, String.class));
            STRING_INIT_HOOK2 = LOOKUP.findStatic(SwitchBootstraps.class, "stringInitHook2",
                                                  MethodType.methodType(MethodHandle.class, CallSite.class));
            STRING_SWITCH_METHOD2 = LOOKUP.findVirtual(StringSwitchCallSite2.class, "doSwitch",
                                                                MethodType.methodType(int.class, String.class));
            INT_EQUALS = LOOKUP.findStatic(SwitchBootstraps.class, "eq",
                                                         MethodType.methodType(boolean.class, int.class, int.class));
            INT_GT = LOOKUP.findStatic(SwitchBootstraps.class, "gt",
                                                         MethodType.methodType(boolean.class, int.class, int.class));
            STRING_EQUALS = LOOKUP.findVirtual(Object.class, "equals",
                                                         MethodType.methodType(boolean.class, Object.class));
            STRING_HASHCODE = LOOKUP.findVirtual(Object.class, "hashCode",
                                                         MethodType.methodType(int.class));

//            STRING_INIT_HOOK3 = LOOKUP.findStatic(SwitchBootstraps.class, "stringInitHook3",
//                                                  MethodType.methodType(MethodHandle.class, CallSite.class));
//            STRING_SWITCH_METHOD3 = LOOKUP.findVirtual(StringSwitchCallSite3.class, "doSwitch",
//                                                                MethodType.methodType(int.class, String.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * TODO
     * @param lookup TODO
     * @param invocationName TODO
     * @param invocationType TODO
     * @param constants TODO
     * @return TODO
     * @throws Throwable TODO
     */
    public static CallSite stringSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      String... constants) throws Throwable {
        return ifTree(lookup, invocationName, invocationType, constants);
//        return hashMap(lookup, invocationName, invocationType, constants);
    }

    /**
     * TODO
     * @param lookup TODO
     * @param invocationName TODO
     * @param invocationType TODO
     * @param constants TODO
     * @return TODO
     * @throws Throwable TODO
     */
    public static CallSite hashMap(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      String... constants) throws Throwable {
        if (invocationType.parameterCount() != 1
            || (!invocationType.returnType().equals(int.class))
            || invocationType.parameterType(0) != String.class)
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(constants);

        constants = constants.clone();

        Map<String, Integer> string2Index = new HashMap<>();

        for (int i = 0; i < constants.length; i++) {
            string2Index.put(constants[i], i);
        }
        return new StringSwitchCallSite(invocationType, string2Index);
    }

    static class StringSwitchCallSite extends ConstantCallSite {
        private final Map<String, Integer> string2Index;

        StringSwitchCallSite(MethodType targetType,
                           Map<String, Integer> string2Index) throws Throwable {
            super(targetType, STRING_INIT_HOOK);
            this.string2Index = string2Index;
        }

        int doSwitch(String selector) {
            return string2Index.getOrDefault(selector, -1);
        }
    }

    private static<T extends CallSite> MethodHandle stringInitHook(T receiver) {
        return STRING_SWITCH_METHOD.bindTo(receiver);
    }

    /**
     * TODO
     * @param lookup TODO
     * @param invocationName TODO
     * @param invocationType TODO
     * @param stringLabels TODO
     * @return TODO
     * @throws Throwable TODO
     */
    public static CallSite binarySearch(MethodHandles.Lookup lookup,
                                        String invocationName,
                                        MethodType invocationType,
                                        String... stringLabels) throws Throwable {
        if (invocationType.parameterCount() != 1
            || (!invocationType.returnType().equals(int.class))
            || (!invocationType.parameterType(0).equals(String.class)))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(stringLabels);
        if (Stream.of(stringLabels).anyMatch(Objects::isNull))
            throw new IllegalArgumentException("null label found");

        assert Stream.of(stringLabels).distinct().count() == stringLabels.length
                : "switch labels are not distinct: " + Arrays.toString(stringLabels);

        return new StringSwitchCallSite2(invocationType, stringLabels);
    }

    static class StringSwitchCallSite2 extends ConstantCallSite {
        private static final Comparator<String> STRING_BY_HASH
                = Comparator.comparingInt(Objects::hashCode);

        private final String[] sortedByHash;
        private final int[] indexes;
        private final boolean collisions;

        StringSwitchCallSite2(MethodType targetType,
                             String[] stringLabels) throws Throwable {
            super(targetType, STRING_INIT_HOOK2);

            // expensive way to index an array
            indexes = IntStream.range(0, stringLabels.length)
                               .boxed()
                               .sorted(Comparator.comparingInt(i -> stringLabels[i].hashCode()))
                               .mapToInt(Integer::intValue)
                               .toArray();
            sortedByHash = new String[indexes.length];
            for (int i=0; i<indexes.length; i++)
                sortedByHash[i] = stringLabels[indexes[i]];

            collisions = IntStream.range(0, sortedByHash.length-1)
                                  .anyMatch(i -> sortedByHash[i].hashCode() == sortedByHash[i + 1].hashCode());
        }

        int doSwitch(String target) {
            if (target == null)
                return -1;

            int index = Arrays.binarySearch(sortedByHash, target, STRING_BY_HASH);
            if (index < 0)
                return indexes.length;
            else if (target.equals(sortedByHash[index])) {
                return indexes[index];
            }
            else if (collisions) {
                int hash = target.hashCode();
                while (index > 0 && sortedByHash[index-1].hashCode() == hash)
                    --index;
                for (; index < sortedByHash.length && sortedByHash[index].hashCode() == hash; index++)
                    if (target.equals(sortedByHash[index]))
                        return indexes[index];
            }

            return indexes.length;
        }
    }

    private static<T extends CallSite> MethodHandle stringInitHook2(T receiver) {
        return STRING_SWITCH_METHOD2.bindTo(receiver);
    }

    /**
     * TODO
     * @param lookup TODO
     * @param invocationName TODO
     * @param invocationType TODO
     * @param stringLabels TODO
     * @return TODO
     * @throws Throwable TODO
     */
    public static CallSite ifTree(MethodHandles.Lookup lookup,
                                        String invocationName,
                                        MethodType invocationType,
                                        String... stringLabels) throws Throwable {
        if (invocationType.parameterCount() != 1
            || (!invocationType.returnType().equals(int.class))
            || (!invocationType.parameterType(0).equals(String.class)))
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(stringLabels);

        if (stringLabels.length == 0) {
            //TODO: should be handled in javac (instead or in addition to this)?
            return new ConstantCallSite(MethodHandles.dropArguments(MethodHandles.constant(int.class, stringLabels.length), 0, String.class));
        }

        if (Stream.of(stringLabels).anyMatch(Objects::isNull))
            throw new IllegalArgumentException("null label found");

        Map<Integer, List<LabelAndCaseNumber>> byHashCode = new TreeMap<>();
        int labelIndex = 0;

        for (String label : stringLabels) {
            int hashCode = label.hashCode();
            byHashCode.computeIfAbsent(hashCode, h -> new ArrayList<>()).add(new LabelAndCaseNumber(label, labelIndex++));
        }
        
        int[] hashCodes = new int[byHashCode.size()];
        int hashIndex = 0;

        for (Integer hashCode : byHashCode.keySet()) {
            hashCodes[hashIndex++] = hashCode;
        }

        assert Stream.of(stringLabels).distinct().count() == stringLabels.length
                : "switch labels are not distinct: " + Arrays.toString(stringLabels);

        MethodHandle switchHandle = produceTests(byHashCode, hashCodes, 0, hashCodes.length - 1);
        switchHandle = MethodHandles.insertArguments(switchHandle, 2, stringLabels.length);
        switchHandle = MethodHandles.permuteArguments(switchHandle, MethodType.methodType(int.class, int.class, String.class), 1, 0);
        MethodHandle hashCodeAdapted = MethodHandles.explicitCastArguments(STRING_HASHCODE, MethodType.methodType(int.class, String.class));
        switchHandle = MethodHandles.foldArguments(switchHandle, hashCodeAdapted);
        return new ConstantCallSite(switchHandle);
    }

    record LabelAndCaseNumber(String label, int caseNumber) {}

    private static MethodHandle produceTests(Map<Integer, List<LabelAndCaseNumber>> byHashCode, int[] hashCodes, int start, int end) {
        if (start == end) {
            MethodHandle hashPredicate = MethodHandles.dropArguments(MethodHandles.dropArguments(MethodHandles.insertArguments(INT_EQUALS, 1, hashCodes[start]), 0, String.class), 2, int.class);
            List<LabelAndCaseNumber> values = byHashCode.get(hashCodes[start]);
            MethodHandle returnDefault = MethodHandles.dropArguments(MethodHandles.identity(int.class), 0, String.class, int.class);
            MethodHandle valueTest = returnDefault;
            for (LabelAndCaseNumber value : values) {
                MethodHandle valuePredicate = MethodHandles.dropArguments(MethodHandles.explicitCastArguments(MethodHandles.insertArguments(STRING_EQUALS, 1, value.label), MethodType.methodType(boolean.class, String.class)), 1, int.class, int.class);
                valueTest = MethodHandles.guardWithTest(valuePredicate, MethodHandles.dropArguments(MethodHandles.constant(int.class, value.caseNumber), 0, String.class, int.class, int.class), valueTest);
                
            }
            return MethodHandles.guardWithTest(hashPredicate, valueTest, returnDefault);
        }
        int middle = (start + end) / 2;
        MethodHandle hashPredicate = MethodHandles.dropArguments(MethodHandles.dropArguments(MethodHandles.insertArguments(INT_GT, 1, hashCodes[middle]), 0, String.class), 2, int.class);
        return MethodHandles.guardWithTest(hashPredicate, produceTests(byHashCode, hashCodes, middle + 1, end), produceTests(byHashCode, hashCodes, start, middle));
    }

    private static boolean eq(int a, int b) { return a == b; }
    private static boolean gt(int a, int b) { return a > b; }

    /**
     * TODO
     * @param <T> TODO
     * @param lookup TODO
     * @param constantName TODO
     * @param constantType TODO
     * @param enumType TODO
     * @param switchKey TODO
     * @return TODO
     * @throws Throwable TODO
     */
    public static <T extends Enum<T>> int[] enumSwitch(MethodHandles.Lookup lookup,
                                    String constantName,
                                    Class<?> constantType,
                                    Class<T> enumType,
                                    String... switchKey) throws Throwable {
        int[] result = new int[enumType.getEnumConstants().length];
        for (int i = 0; i < switchKey.length; i++) {
            String key = switchKey[i];
            try {
                result[Enum.valueOf(enumType, key).ordinal()] = i + 1;
            } catch (IllegalArgumentException ignore) {
            }
        }
        return result;
    }
}
