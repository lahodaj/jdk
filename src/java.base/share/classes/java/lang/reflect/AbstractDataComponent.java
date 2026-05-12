/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.Annotation;

/**
 * XXX
 *
 * @since 26
 */
public abstract class AbstractDataComponent implements AnnotatedElement {

    AbstractDataComponent() {}

    /**
     * Returns the name of this component.
     *
     * @return the name of this component
     */
    public abstract String getName();

    /**
     * Returns a {@code Class} that identifies the declared type for this
     * component.
     *
     * @return a {@code Class} identifying the declared type of the component
     * represented by this component
     */
    public abstract Class<?> getType();

    /**
     * Returns a {@code String} that describes the generic type signature for
     * this component.
     *
     * @return a {@code String} that describes the generic type signature for
     * this component
     *
     * @jvms 4.7.9.1 Signatures
     */
    public abstract String getGenericSignature();

    /**
     * Returns a {@code Type} object that represents the declared type for
     * this component.
     *
     * <p>If the declared type of the component is a parameterized type,
     * the {@code Type} object returned reflects the actual type arguments used
     * in the source code.
     *
     * <p>If the type of the underlying component is a type variable or a
     * parameterized type, it is created. Otherwise, it is resolved.
     *
     * @return a {@code Type} object that represents the declared type for
     *         this component
     * @throws GenericSignatureFormatError if the generic component
     *         signature does not conform to the format specified in
     *         <cite>The Java Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if the generic type
     *         signature of the underlying component refers to a non-existent
     *         type declaration
     * @throws MalformedParameterizedTypeException if the generic
     *         signature of the underlying component refers to a parameterized
     *         type that cannot be instantiated for any reason
     */
    public abstract Type getGenericType();

    /**
     * Returns an {@code AnnotatedType} object that represents the use of a type to specify
     * the declared type of this component.
     *
     * @return an object representing the declared type of this component
     */
    public abstract AnnotatedType getAnnotatedType();

    /**
     * Returns a {@code Method} that represents the accessor for this
     * component.
     *
     * @return a {@code Method} that represents the accessor for this
     * component
     */
    public abstract Method getAccessor();

    /**
     * {@inheritDoc}
     * <p>Note that any annotation returned by this method is a
     * declaration annotation.
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public abstract <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * {@inheritDoc}
     * <p>Note that any annotations returned by this method are
     * declaration annotations.
     */
    @Override
    public abstract Annotation[] getAnnotations();

    /**
     * {@inheritDoc}
     * <p>Note that any annotations returned by this method are
     * declaration annotations.
     */
    @Override
    public abstract Annotation[] getDeclaredAnnotations();

    /**
     * Returns a string describing this component. The format is
     * the component type, followed by a space, followed by the name
     * of the component.
     * For example:
     * <pre>
     *    java.lang.String name
     *    int age
     * </pre>
     *
     * @return a string describing this component
     */
    public String toString() {
        return (getType().getTypeName() + " " + getName());
    }

    /**
     * Returns the class which declares this component.
     *
     * @return The class declaring this component.
     */
    public abstract Class<?> getDeclaringClass();
}
