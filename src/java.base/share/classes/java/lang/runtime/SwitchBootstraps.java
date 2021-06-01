/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.stream.Stream;

import jdk.internal.javac.PreviewFeature;

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
 *
 * @since 17
 */
@PreviewFeature(feature=PreviewFeature.Feature.SWITCH_PATTERN_MATCHING)
public class SwitchBootstraps {

    private SwitchBootstraps() {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle DO_SWITCH;

    static {
        try {
            DO_SWITCH = LOOKUP.findStatic(SwitchBootstraps.class, "doSwitch",
                                           MethodType.methodType(int.class, Object.class, int.class, Object[].class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Bootstrap method for linking an {@code invokedynamic} call site that
     * implements a {@code switch} on a reference-typed target.  The static
     * arguments are a varargs array of case labels. Constants of type {@code String} or
     * {@code Integer} and {@code Class} instances are accepted.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName The invocation name, which is ignored.  When used with
     *                       {@code invokedynamic}, this is provided by the
     *                       {@code NameAndType} of the {@code InvokeDynamic}
     *                       structure and is stacked automatically by the VM.
     * @param invocationType The invocation type of the {@code CallSite}.  This
     *                       method type should have a single parameter of
     *                       a reference type, and return {@code int}.  When
     *                       used with {@code invokedynamic}, this is provided by
     *                       the {@code NameAndType} of the {@code InvokeDynamic}
     *                       structure and is stacked automatically by the VM.
     * @param labels case labels - {@code String} and {@code Integer} constants,
     *                        {@code Class} instances or {@code null}, in any combination
     * @return a {@code CallSite}, which accepts two parameters: one is an instance
     *         of the target type, and second is a restart index. It returns the
     *         index into {@code labels} of the target value, if the target
     *         is an instance of any of the types or equal to any of the constants, {@literal -1} if the target
     *         value is {@code null}, or {@code types.length} if the target value
     *         is not an instance of any of the types or equal to any of the constants. Will return
     *         an index that is greater or equal to the restart index provided.
     * @throws NullPointerException if any required argument is null
     * @throws IllegalArgumentException if any labels are null, or if the
     * invocation type is not {@code (T)int for some reference type {@code T}}
     * @throws Throwable if there is any error linking the call site
     */
    public static CallSite typeSwitch(MethodHandles.Lookup lookup,
                                      String invocationName,
                                      MethodType invocationType,
                                      Object... labels) throws Throwable {
        if (invocationType.parameterCount() != 2
            || (!invocationType.returnType().equals(int.class))
            || invocationType.parameterType(0).isPrimitive())
            throw new IllegalArgumentException("Illegal invocation type " + invocationType);
        requireNonNull(labels);

        labels = labels.clone();
        Stream.of(labels).forEach(SwitchBootstraps::verifyLabel);

        MethodHandle target = MethodHandles.insertArguments(DO_SWITCH, 2, (Object) labels);
        return new ConstantCallSite(target);
    }

    private static void verifyLabel(Object label) {
        Class<?> labelClass = label.getClass();
        if (labelClass != Class.class &&
            labelClass != String.class &&
            labelClass != Integer.class &&
            !labelClass.isEnum()) {
            throw new IllegalArgumentException("label with illegal type found: " + label.getClass());
        }
    }

    private static int doSwitch(Object target, int startIndex, Object[] labels) {
        if (target == null)
            return -1;

        // Dumbest possible strategy
        Class<?> targetClass = target.getClass();
        for (int i = startIndex; i < labels.length; i++) {
            Object label = labels[i];
            if (label == null) {
                //ignore
            } else if (label instanceof Class<?> c) {
                if (c.isAssignableFrom(targetClass))
                    return i;
            } else {
                if (label instanceof Integer constant) {
                    if (target instanceof Number input && constant.intValue() == input.intValue()) {
                        return i;
                    }
                    if (target instanceof Character input && constant.intValue() == input.charValue()) {
                        return i;
                    }
                } else if (label instanceof Enum<?>) {
                    if (label == target) {
                        return i;
                    }
                } else if (label.equals(target)) {
                    return i;
                }
            }
        }

        return labels.length;
    }

    /**
     * Returns an {@code enum} constant of the type specified by {@code type}
     * with the name specified by {@code name}.
     *
     * @param lookup Represents a lookup context with the accessibility
     *               privileges of the caller.  When used with {@code invokedynamic},
     *               this is stacked automatically by the VM.
     * @param invocationName The invocation name, which is ignored.  When used with
     *                       {@code invokedynamic}, this is provided by the
     *                       {@code NameAndType} of the {@code InvokeDynamic}
     *                       structure and is stacked automatically by the VM.
     * @param invocationClass The invocation Class, which is ignored. When
     *                       used with {@code invokedynamic}, this is stacked
     *                       automatically by the VM.
     * @param name the name of the constant to return, which must exactly match
     * an enum constant in the specified type.
     * @param type the {@code Class} object describing the enum type for which
     * a constant is to be returned
     * @param <E> The enum type for which a constant value is to be returned
     * @return the enum constant of the specified enum type with the
     * specified name, or null if not found
     * @throws IllegalAccessError if the declaring class or the field is not
     * accessible to the class performing the operation
     * @see Enum#valueOf(Class, String)
     */
    public static <E extends Enum<E>> E enumConstant(MethodHandles.Lookup lookup,
                                                     String invocationName,
                                                     Class<?> invocationClass,
                                                     String name,
                                                     Class<E> type) {
        try {
            return ConstantBootstraps.enumConstant(lookup, name, type);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
