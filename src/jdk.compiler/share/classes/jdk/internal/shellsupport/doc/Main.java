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
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Main {

    static Map<String, String> classDictionary = new HashMap<>();
    static BiConsumer<String, String> persistElement = classDictionary::putIfAbsent;
    static BiConsumer<JavadocHelper, Element> checkElement = (javadocHelper, element) -> {
        try {
            String comment = javadocHelper.getResolvedDocComment(element);
            String sinceVersion = comment != null ? extractSinceVersion(comment) : null;
            String mappedVersion = classDictionary.get(getElementName(element));
            checkEquals(sinceVersion, mappedVersion, element.getSimpleName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    };


    private static String extractSinceVersion(String documentation) {
        Pattern pattern = Pattern.compile("@since\\s+(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(documentation);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "@since version not found";
        }
    }

    public static void main(String[] args) throws IOException {
        String sourcePath = args[0];
        String outputPath = args[1];
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= 21; i++) {
            try {
                JavacTask ct = (JavacTask) tool.getTask(null, null, null, List.of("--release", String.valueOf(i)), null, Collections.singletonList(new JavaSource()));
                ct.analyze();
                String version = String.valueOf(i);
                ct.getElements().getAllModuleElements().forEach(me -> processModule(true, me, version, null));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JavacTask ct = (JavacTask) tool.getTask(null, null, null,
                List.of("--module-source-path", sourcePath, "--system", "none", "-m", "java.base", "-d", outputPath),
                null, Collections.singletonList(new JavaSource()));
        ct.analyze();
        ct.getElements().getTypeElement("java.lang.Object");
        Path sourcesRoot = Paths.get(sourcePath);
        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourcesRoot)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    sources.add(p);
                }
            }
        }
        try (JavadocHelper docHelper = JavadocHelper.create(ct, sources)) {
            ct.getElements().getAllModuleElements().forEach(me -> processModule(false, me, null, docHelper));
        }
    }

    private static void checkEquals(String sinceVersion, String mappedVersion, String simpleName) {
        try {
            System.err.println("For  Element: " + simpleName);
            System.err.println("sinceVersion: " + sinceVersion + "\t mappedVersion: " + mappedVersion);
            if (sinceVersion == null || mappedVersion == null) {
                return;
            }
            if (sinceVersion.contains(".")) {
                String[] x = sinceVersion.split("[.]");
                sinceVersion = x[1];
                if (Integer.parseInt(sinceVersion) < 9)
                    sinceVersion = "9";
            }
            if (!sinceVersion.equals(mappedVersion)) {
                System.err.println("Wrong since version " + sinceVersion + " instead of " + mappedVersion);
            }
        } catch (NumberFormatException e) {
            System.err.println("Element: " + simpleName + "\t Invalid number: " + sinceVersion);
        }
    }


    private static void processModule(boolean shouldPersist, ModuleElement me, String s, JavadocHelper javadocHelper) {
        for (ModuleElement.ExportsDirective ed : ElementFilter.exportsIn(me.getDirectives())) {
            if (ed.getTargetModules() == null) {
                analyzePackage(ed.getPackage(), s, shouldPersist, javadocHelper);
            }
        }
    }


    private static void analyzePackage(PackageElement pe, String s, boolean shouldPersist, JavadocHelper javadocHelper) {
        for (TypeElement te : ElementFilter.typesIn(pe.getEnclosedElements())) {
            analyzeClass(te, s, shouldPersist, javadocHelper);
        }
    }

    private static void analyzeClass(TypeElement te, String version, boolean shouldPersist, JavadocHelper javadocHelper) {
        String uniqueElementId = getElementName(te);
        if (shouldPersist) {
            persistElement.accept(uniqueElementId, version);
        } else {
            checkElement.accept(javadocHelper, te);
        }
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isField() ||
                        element.getKind() == ElementKind.METHOD ||
                        element.getKind() == ElementKind.CONSTRUCTOR)
                .forEach(element -> {
                    String elementId = getElementName(element);
                    if (shouldPersist) {
                        persistElement.accept(elementId, version);
                    } else {
                        Set<Modifier> modifiers = element.getModifiers();
                        if (modifiers.contains(Modifier.PUBLIC)) {
                            checkElement.accept(javadocHelper, te);
                        }
                    }
                });
        te.getEnclosedElements().stream()
                .filter(element -> element.getKind().isClass())
                .map(TypeElement.class::cast)
                .forEach(nestedClass -> analyzeClass(nestedClass, version, shouldPersist, javadocHelper));
    }

    private static String getElementName(Element element) {
        String s = "";
        if (element.getKind() == ElementKind.CLASS) {
            TypeElement typeElement = (TypeElement) element;
            s = "class:" + typeElement.getQualifiedName().toString();
        } else if (element.getKind().isField()) {
            VariableElement variableElement = (VariableElement) element;
            s = "field:" + variableElement.getEnclosingElement().toString() + ":" + element.getSimpleName();
        } else if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR) {
            ExecutableElement executableElement = (ExecutableElement) element;
            String binaryName = ((TypeElement) executableElement.getEnclosingElement()).getQualifiedName().toString();
            String methodName = element.getSimpleName().toString();
            String descriptor = executableElement.toString();
            s = "method:" + binaryName + ":" + methodName + ":" + descriptor;
        }
        return s;
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