/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.shellsupport.doc;

import com.sun.source.util.JavacTask;

import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@FunctionalInterface
interface MyConsumer<T, U, V, K> {
    void accept(T t, U u, V v, K k);
}

public class Main {
    static Map<String, String> classDictionary = new HashMap<>();

    static BiConsumer<String, String> persistElement = classDictionary::putIfAbsent;
    static MyConsumer<JavadocHelper, TypeElement, Element, Types> checkElement = (javadocHelper, te, element, types) -> {
        String comment = null;
        try {
            comment = javadocHelper.getResolvedDocComment(element);
            String sinceVersion = comment != null ? extractSinceVersion(comment) : null;
            String mappedVersion = classDictionary.get(getElementName(te, element, types));
            checkEquals(sinceVersion, mappedVersion, getElementName(te, element, types));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    private static String extractSinceVersion(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        String sourcePath = args[0];
        String outputPath = args[1];
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        Elements elements;
        for (int i = 9; i <= 23; i++) {
            try {
                JavacTask ct = (JavacTask) tool.getTask(null, null, null, List.of("--release", String.valueOf(i)), null, Collections.singletonList(new JavaSource()));
                ct.analyze();
                String version = String.valueOf(i);
                ct.getElements().getAllModuleElements().forEach(me -> processModule(me, version, ct));
//                var x = classDictionary.entrySet().stream()
//                        .filter(entry -> entry.getKey().startsWith("method:java.io.PrintStream"))
//                        .map(Map.Entry::getKey)
//                        .collect(Collectors.toList());
//                System.out.println("^ for debugging purposes");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        JavacTask ct = (JavacTask) tool.getTask(null, null, null, List.of("--module-source-path", sourcePath, "--system", "none", "-m", "java.base", "-d", outputPath), null, Collections.singletonList(new JavaSource()));
        ct.analyze();

        Path sourcesRoot = Paths.get(sourcePath);
        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourcesRoot)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    sources.add(p);
                }
            }
        }
        ct.getElements().getAllModuleElements().parallelStream().forEach(me -> processModule(me, ct, sources));
    }

    private static void checkEquals(String sinceVersion, String mappedVersion, String simpleName) {
        try {
            //      System.err.println("For  Element: " + simpleName);
            //      System.err.println("sinceVersion: " + sinceVersion + "\t mappedVersion: " +
            // mappedVersion);
            if (sinceVersion == null) {
                return;
            }
            if (mappedVersion == null) {
                System.out.println("check for why mapped version is null for" + simpleName);
                return;
            }
            if (sinceVersion.contains(".")) {
                String[] x = sinceVersion.split("[.]");
                sinceVersion = x[1];
                if (Integer.parseInt(sinceVersion) < 9) sinceVersion = "9";
            }
            if (!sinceVersion.equals(mappedVersion)) {
                System.err.println("For  Element: " + simpleName);
                System.err.println("Wrong since version " + sinceVersion + " instead of " + mappedVersion);
            }
        } catch (NumberFormatException e) {
            System.err.println("Element: " + simpleName + "\t Invalid number: " + sinceVersion);
        }
    }

    private static void processModule(ModuleElement moduleElement, String s, JavacTask ct) {
        processModule(true, moduleElement, s, ct, null);
    }

    private static void processModule(ModuleElement moduleElement, JavacTask ct, List<Path> sources) {
        processModule(false, moduleElement, null, ct, sources);
    }

    private static void processModule(boolean shouldPersist, ModuleElement moduleElement, String s, JavacTask ct, List<Path> sources) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(moduleElement.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackage(ed.getPackage(), s, shouldPersist, ct, sources);
            }
        }
    }

    private static void analyzePackage(PackageElement pe, String s, boolean shouldPersist, JavacTask ct, List<Path> sources) {
        List<TypeElement> typeElements = ElementFilter.typesIn(pe.getEnclosedElements());
        for (TypeElement te : typeElements) {
            if (!shouldPersist) {
                try (JavadocHelper javadocHelper = JavadocHelper.create(ct, sources)) {
                    analyzeClass(te, s, shouldPersist, javadocHelper, ct.getTypes(), ct.getElements());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                analyzeClass(te, s, shouldPersist, null, ct.getTypes(), ct.getElements());
            }
        }
    }

    private static void analyzeClass(TypeElement te, String version, boolean shouldPersist, JavadocHelper javadocHelper, Types types, Elements elements) {
        if (!te.getModifiers().contains(Modifier.PUBLIC)) {
            return;
        }
        String uniqueElementId = getElementName(te, te, types);
        if (shouldPersist) {
            persistElement.accept(uniqueElementId, version);
        } else {
            checkElement.accept(javadocHelper, te, te, types);
        }


        elements.getAllMembers(te).stream().filter(element -> element.getKind().isField() || element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR).forEach(element -> {
            String elementId = getElementName(te, element, types);
            if (shouldPersist) {
                persistElement.accept(elementId, version);
            } else {
                if (element.getModifiers().contains(Modifier.PUBLIC)) {
                    checkElement.accept(javadocHelper, te, element, types);
                }
            }
        });
        te.getEnclosedElements().stream().filter(element -> element.getKind().isClass()).map(TypeElement.class::cast).forEach(nestedClass -> analyzeClass(nestedClass, version, shouldPersist, javadocHelper, types, elements));
    }

    private static String getElementName(TypeElement te, Element element, Types types) {
        String prefix = "";
        String suffix = "";

        if (element.getKind().isField()) {
            prefix = "field";
            suffix = ":" + te.getQualifiedName() + ":" + element.getSimpleName();
        } else if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR) {
            prefix = "method";
            ExecutableElement executableElement = (ExecutableElement) element;
            String methodName = executableElement.getSimpleName().toString();
            String descriptor = executableElement.getParameters().stream().map(p -> types.erasure(p.asType()).toString()).collect(Collectors.joining(",", "(", ")"));
            suffix = ":" + te.getQualifiedName() + ":" + methodName + ":" + descriptor;
        } else if (element.getKind().isDeclaredType()) {
            prefix = "class";
            suffix = ":" + te.getQualifiedName();
        }

        return prefix + suffix;
    }

    private static class JavaSource extends SimpleJavaFileObject {
        private static final String TEXT = "";

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return TEXT;
        }
    }
}
