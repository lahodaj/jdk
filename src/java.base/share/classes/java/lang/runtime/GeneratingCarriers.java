/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.vm.annotation.Stable;

/**
 * A <em>carrier</em> is an opaque object that can be used to store component values
 * while avoiding primitive boxing associated with collection objects. Component values
 * can be primitive or Object.
 * <p>
 * Clients can create new carrier instances by describing a carrier <em>shape</em>, that
 * is, a {@linkplain MethodType method type} whose parameter types describe the types of
 * the carrier component values, or by providing the parameter types directly.
 *
 * {@snippet :
 * // Create a carrier for a string and an integer
 * CarrierElements elements = CarrierFactory.of(String.class, int.class);
 * // Fetch the carrier constructor MethodHandle
 * MethodHandle initializingConstructor = elements.initializingConstructor();
 * // Fetch the list of carrier component MethodHandles
 * List<MethodHandle> components = elements.components();
 *
 * // Create an instance of the carrier with a string and an integer
 * Object carrier = initializingConstructor.invokeExact("abc", 10);
 * // Extract the first component, type string
 * String string = (String)components.get(0).invokeExact(carrier);
 * // Extract the second component, type int
 * int i = (int)components.get(1).invokeExact(carrier);
 * }
 *
 * Alternatively, the client can use static methods when the carrier use is scattered.
 * This is possible since {@link Carriers} ensures that the same underlying carrier
 * class is used when the same component types are provided.
 *
 * {@snippet :
 * // Describe carrier using a MethodType
 * MethodType mt = MethodType.methodType(Object.class, String.class, int.class);
 * // Fetch the carrier constructor MethodHandle
 * MethodHandle constructor = Carriers.constructor(mt);
 * // Fetch the list of carrier component MethodHandles
 * List<MethodHandle> components = Carriers.components(mt);
 * }
 *
 * @implNote The strategy for storing components is deliberately left unspecified
 * so that future improvements will not be hampered by issues of backward compatibility.
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 *
 * XXX: cache eviction
 * XXX: names, performance
 */
public final class GeneratingCarriers {

    private GeneratingCarriers() {
    }

    /**
     * {@return the combination {@link MethodHandle} of the constructor and initializer
     * for the carrier representing {@code methodType}. The carrier constructor/initializer
     * will always take the component values and a return type of {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    static public MethodHandle initializingConstructor(MethodType methodType) {
        try {
            MethodType simplifiedShape = simplifiedCarrierShape(methodType);
            return MethodHandles.lookup().unreflectConstructor(generateCarrierClass(simplifiedShape).getDeclaredConstructor(simplifiedShape.parameterArray())).asType(methodType);
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
    }

    /**
     * {@return a component accessor {@link MethodHandle} for component {@code i} of the
     * carrier representing {@code methodType}. The receiver type of the accessor will always
     * be {@link Object} }
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     * @param i           component index
     *
     * @throws IllegalArgumentException if {@code i} is out of bounds
     */
    public static MethodHandle component(MethodType methodType, int i) {
        try {
            MethodType simplifiedShape = simplifiedCarrierShape(methodType);
            return MethodHandles.lookup().unreflect(generateCarrierClass(simplifiedShape).getDeclaredMethod("component" + i)).asType(MethodType.methodType(simplifiedShape.parameterType(i), Object.class));
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
    }

    /**{@return a MethodHandle, which accepts the carrier as the first parameter,
     * and a MethodHandle as the second; the provided MethodHandle will get all
     * the carrier component values as parameters.}
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    public static MethodHandle componentInvoker(MethodType methodType) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /**
     * {@return a {@link MethodHandle MethodHandle} which accepts a carrier object
     * matching the given {@code methodType} which when invoked will return a newly
     * created object array containing the boxed component values of the carrier object.}
     *
     * @param methodType  {@link MethodType} whose parameter types supply the shape of the
     *                    carrier's components
     */
    public static MethodHandle boxedComponentValueArray(MethodType methodType) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private static final Map<MethodType, Class<?>> CARRIER_SHAPE_2_CARRIER = new HashMap<>();
    private static final ClassDesc JAVA_LANG_OBJECT = ClassDesc.of("java.lang.Object");
    private static final MethodTypeDesc NO_PARAMS_RETURN_VOID = MethodTypeDesc.ofDescriptor("()V");

    private static synchronized Class<?> generateCarrierClass(MethodType carrierShape) throws IllegalAccessException { //TODO: synchronization!
        Class<?> existing = CARRIER_SHAPE_2_CARRIER.get(carrierShape);

        if (existing != null) {
            return existing;
        }

        //todo: better class name
        StringBuilder name = new StringBuilder();
        name.append("$$$$");
        carrierShape.parameterList().stream().map(c -> c.getName().replace('.', '_') + '$').forEach(name::append);
        MethodHandles.Lookup targetLookup = MethodHandles.lookup(); //XXX: where to put the generated classes?
        ClassDesc carrierClassDesc = ClassDesc.of(targetLookup.lookupClass().getPackageName(), name.toString());
        byte[] bytecode = ClassFile.of().build(carrierClassDesc, cb -> {
            cb.withFlags(0);
            cb.withSuperclass(JAVA_LANG_OBJECT);
            MethodTypeDesc carrierConstructorDesc = carrierShape.changeReturnType(void.class).describeConstable().orElseThrow();
            cb.withMethod("<init>", carrierConstructorDesc, AccessFlag.PUBLIC.mask(), mb -> {
                mb.withCode(code -> {
                    code.aload(0);
                    code.invokespecial(JAVA_LANG_OBJECT, "<init>", NO_PARAMS_RETURN_VOID);

                    int slot = 1;

                    for (int p = 0; p < carrierShape.parameterCount(); p++) {
                        ClassDesc paramType = carrierConstructorDesc.parameterType(p);
                        code.aload(0);
                        code.loadLocal(TypeKind.from(paramType), slot);
                        code.putfield(carrierClassDesc, "component" + p, paramType);
                        slot += TypeKind.from(paramType).slotSize();
                    }

                    code.return_();
                });
            });
            for (int p = 0; p < carrierShape.parameterCount(); p++) {
                ClassDesc paramType = carrierConstructorDesc.parameterType(p);
                String componentStoreName = "component" + p;
                cb.withMethod(componentStoreName, MethodTypeDesc.of(paramType), AccessFlag.PUBLIC.mask(), mb -> {
                    mb.withCode(code -> {
                        code.aload(0);
                        code.getfield(carrierClassDesc, componentStoreName, paramType);
                        code.return_(TypeKind.from(paramType));
                    });
                });
                cb.withField(componentStoreName, paramType, AccessFlag.PRIVATE.mask());
            }
        });

        Class<?> defined = targetLookup.defineHiddenClass(bytecode, true).lookupClass();

        CARRIER_SHAPE_2_CARRIER.put(carrierShape, defined);

        return defined;
    }

    private static MethodType simplifiedCarrierShape(MethodType carrierShape) {
        return MethodType.methodType(carrierShape.returnType(), carrierShape.parameterList().stream().map(c -> c.isPrimitive() ? c : Object.class).toList());
    }

    private record Carrier(@Stable Object[] data) {}
}
