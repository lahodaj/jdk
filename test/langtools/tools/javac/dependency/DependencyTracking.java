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

/**
 * @test
 * @bug 9999999
 * @summary Check the behavior of dependency tracking
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main DependencyTracking
*/

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class DependencyTracking extends TestRunner {

    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new DependencyTracking().runTests();
    }

    DependencyTracking() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testInternalChanges(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          module m {
                          }
                          """,
                          """
                          package test;
                          public class Dep {
                              public static void printHello(Object o) {}
                          }
                          """,
                          """
                          package test;
                          public class Test {
                              public static void main(String... args) {
                                  Dep.printHello("");
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-m", "m",
                     "--module-source-path", "m=" + src.toAbsolutePath().toString())
            .outdir(classes)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        Map<String, Long> beforeRecompilation;
        Map<String, Long> afterRecompilation;

        beforeRecompilation = path2TimeStamp(classes);

        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Dep {
                              public static void printHello(Object o) {
                                  System.err.println(o);
                              }
                          }
                          """);

        new JavacTask(tb)
            .options("-m", "m",
                     "--module-source-path", "m=" + src.toAbsolutePath().toString())
            .outdir(classes)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        afterRecompilation = path2TimeStamp(classes);

        assertRecompiled(recompiled(beforeRecompilation, afterRecompilation),
                         "m/test/Dep.class");

        beforeRecompilation = afterRecompilation;

        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Dep {
                              public static void printHello(Object o) {
                                  System.err.println(o);
                              }
                              public static void printHello(String s) {}
                          }
                          """);

        new JavacTask(tb)
            .options("-m", "m",
                     "--module-source-path", "m=" + src.toAbsolutePath().toString())
            .outdir(classes)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        afterRecompilation = path2TimeStamp(classes);

        assertRecompiled(recompiled(beforeRecompilation, afterRecompilation),
                         "m/module-info.class", "m/test/Dep.class", "m/test/Test.class");
    }

    @Test
    public void testDependencyChanges(Path base) throws Exception {
        Path lib = base.resolve("lib");
        Path libSrc = lib.resolve("lib-src");
        Path libClasses = lib.resolve("lib-classes");
        tb.writeJavaFiles(libSrc,
                          """
                          module lib {
                              exports api;
                          }
                          """,
                          """
                          package api;
                          public class Api {
                              public static void printHello(Object o) {}
                          }
                          """,
                          """
                          package impl;
                          public class Impl {
                              public static void printHello(Object o) {}
                          }
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .options("-m", "lib",
                     "--module-source-path", "lib=" + libSrc.toAbsolutePath().toString())
            .outdir(libClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        Path use = base.resolve("use");
        Path useSrc = use.resolve("use-src");
        Path useClasses = use.resolve("use-classes");
        tb.writeJavaFiles(useSrc,
                          """
                          module use {
                              requires lib;
                          }
                          """,
                          """
                          package test;
                          public class Test {
                              public static void main(String... args) {
                                  api.Api.printHello("");
                              }
                          }
                          """);

        Files.createDirectories(useClasses);

        new JavacTask(tb)
            .options("-m", "use",
                     "--module-path", libClasses.toString(),
                     "--module-source-path", "use=" + useSrc.toAbsolutePath().toString())
            .outdir(useClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        Map<String, Long> beforeRecompilation;
        Map<String, Long> afterRecompilation;

        beforeRecompilation = path2TimeStamp(useClasses);

        tb.writeJavaFiles(libSrc,
                          """
                          package impl;
                          public class Impl {
                              public static void printHello(Object o) {}
                              public static void printHello(String s) {}
                          }
                          """);

        new JavacTask(tb)
            .options("-m", "lib",
                     "--module-source-path", "lib=" + libSrc.toAbsolutePath().toString())
            .outdir(libClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        new JavacTask(tb)
            .options("-m", "use",
                     "--module-path", libClasses.toString(),
                     "--module-source-path", "use=" + useSrc.toAbsolutePath().toString())
            .outdir(useClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        afterRecompilation = path2TimeStamp(useClasses);

        assertRecompiled(recompiled(beforeRecompilation, afterRecompilation));

        beforeRecompilation = afterRecompilation;

        tb.writeJavaFiles(libSrc,
                          """
                          package api;
                          public class Api {
                              public static void printHello(Object o) {}
                              public static void printHello(String s) {}
                          }
                          """);

        new JavacTask(tb)
            .options("-m", "lib",
                     "--module-source-path", "lib=" + libSrc.toAbsolutePath().toString())
            .outdir(libClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        new JavacTask(tb)
            .options("-m", "use",
                     "--module-path", libClasses.toString(),
                     "--module-source-path", "use=" + useSrc.toAbsolutePath().toString())
            .outdir(useClasses)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        afterRecompilation = path2TimeStamp(useClasses);

        assertRecompiled(recompiled(beforeRecompilation, afterRecompilation),
                         "use/module-info.class", "use/test/Test.class");
    }

    private static Map<String, Long> path2TimeStamp(Path dir) throws IOException {
        Map<String, Long> result = new HashMap<>();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                result.put(dir.relativize(file).toString(), attrs.lastModifiedTime().toMillis());
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private static Set<String> recompiled(Map<String, Long> beforeRecompilation, Map<String, Long> afterRecompilation) {
        Set<String> result = new TreeSet<>();
        for (Map.Entry<String, Long> e : beforeRecompilation.entrySet()) {
            if (!Objects.equals(afterRecompilation.get(e.getKey()), e.getValue())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    private static void assertRecompiled(Set<String> actual, String... expected) {
        Set<String> expectedSet = new TreeSet<>(Arrays.asList(expected));
        if (!Objects.equals(actual, expectedSet)) {
            throw new AssertionError("Unexpected result, expected: " + expectedSet + ", but got: " + actual);
        }
    }
}
