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
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

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

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static {
        try {
            STRING_INIT_HOOK = LOOKUP.findStatic(SwitchBootstraps.class, "stringInitHook",
                                                  MethodType.methodType(MethodHandle.class, CallSite.class));
            STRING_SWITCH_METHOD = LOOKUP.findVirtual(StringSwitchCallSite.class, "doSwitch",
                                                                MethodType.methodType(int.class, String.class));
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
