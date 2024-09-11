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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class GenerateSample {

    private static final int CASE_NESTING_CONSTANT = 3;
    private static final int CLASSES = 500;

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(".");
        Path src = root.resolve("src");
        Path m1 = src.resolve("m1");
        Path m2 = src.resolve("m2");

        Files.createDirectories(m1);

        try (Writer out = Files.newBufferedWriter(m1.resolve("module-info.java"))) {
            out.write("module m1 { exports api; }");
        }

        Path apiPackage = m1.resolve("api");

        Files.createDirectories(apiPackage);

        try (Writer out = Files.newBufferedWriter(apiPackage.resolve("API.java"))) {
            out.write("""
                      package api;
                      public class API {
                          public static void apiMethod() {}
                      }
                      """);
        }

        Files.createDirectories(m2);

        try (Writer out = Files.newBufferedWriter(m2.resolve("module-info.java"))) {
            out.write("module m2 { requires m1; }");
        }

        for (Path module : new Path[] {m1, m2}) {
            String packageName = "test" + module.getFileName().toString();
            Path packageDirectory = module.resolve(packageName);

            Files.createDirectories(packageDirectory);

            try (Writer out = Files.newBufferedWriter(packageDirectory.resolve("InternalAPI.java"))) {
                out.write("""
                          package ${package};
                          public class InternalAPI {
                              public static void apiMethod() {}
                          }
                          """.replace("${package}", packageName));
            }

            Set<String> variants = createDeeplyNestedVariants(CASE_NESTING_CONSTANT);

            for (int c = 0; c < CLASSES; c++) {
                String className = "Test" + c;
                Path targetFile = packageDirectory.resolve(className + ".java");

                try (Writer out = Files.newBufferedWriter(targetFile)) {
                    StringBuilder cases = new StringBuilder();

                    for (String variant : variants) {
                        cases.append("case ");
                        String[] parts = variant.split("_");
                        for (int i = 0; i < parts.length; i++) {
                            cases.append(parts[i]);
                            if (i + 1 < parts.length || i == 0) {
                                cases.append("v" + i);
                            }
                        }
                        cases.append(" -> System.err.println();\n");
                    }
                    String code = """
                           package ${package};
                           public class ${className} {
                               {
                                  InternalAPI.apiMethod();
                               }
                               sealed interface I {}
                               final class C implements I {}
                               record R(I c1, I c2, I c3) implements I {}

                               void test(I i) {
                                   switch (i) {
                                       ${cases}
                                   }
                               }
                           }
                           """.replace("${className}", className)
                              .replace("${package}", packageName)
                              .replace("${cases}", cases);

                    out.write(code);
                }
            }
        }
    }
    

    private static Set<String> createDeeplyNestedVariants(int nestingLevel) {
        Set<String> variants = new HashSet<>();
        variants.add("C _");
        variants.add("R(I _, I _, I _)");
        for (int n = 0; n < nestingLevel; n++) {
            Set<String> newVariants = new HashSet<>();
            for (String variant : variants) {
                if (variant.contains(", I _")) {
                    newVariants.add(variant.replaceFirst(", I _", ", C _"));
                    newVariants.add(variant.replaceFirst(", I _", ", R(I _, I _, I _)"));
                } else {
                    newVariants.add(variant);
                }
            }
            variants = newVariants;
        }
        for (int n = 0; n < nestingLevel; n++) {
            Set<String> newVariants = new HashSet<>();
            for (String variant : variants) {
                if (variant.contains("I _")) {
                    newVariants.add(variant.replaceFirst("I _", "C _"));
                    newVariants.add(variant.replaceFirst("I _", "R(I _, I _, I _)"));
                } else {
                    newVariants.add(variant);
                }
            }
            variants = newVariants;
        }
        OUTER: for (int i = 0; i < nestingLevel; i++) {
            Iterator<String> it = variants.iterator();
            while (it.hasNext()) {
                String current = it.next();
                if (current.contains("(I _, I _, I _)")) {
                    it.remove();
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(C _, I _, C _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(C _, I _, R _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(R _, I _, C _)"));
                    variants.add(current.replaceFirst("\\(I _, I _, I _\\)", "(R _, I _, R _)"));
                    continue OUTER;
                }
            }
        }

        return variants;
    }

}
