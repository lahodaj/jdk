/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary CP
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library /tools/lib
 * @run junit CP
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.ToolBox;

import org.junit.Test;

public class CP {
    private ToolBox tb;

    public CP() {
        tb = new ToolBox();
    }

    @Test
    public void testPatternsInJava() throws Exception {
        runTest("""
                public record R(int val) {
                    static void main() {
                        Object o = new R(1);
                        if (!(o instanceof R(1))) {
                            throw new AssertionError("1");
                        }
                        if (o instanceof R(0)) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testString() throws Exception {
        runTest("""
                public record R(String val) {
                    static void main() {
                        Object o = new R("a");
                        if (!(o instanceof R("a"))) {
                            throw new AssertionError("1");
                        }
                        if (o instanceof R("")) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testFloat() throws Exception {
        runTest("""
                public record R(float val) {
                    static void main() {
                        Object o = new R(Float.NaN);
                        if (!(o instanceof R(Float.NaN))) {
                            throw new AssertionError("1");
                        }
                        if (o instanceof R(0)) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testDouble() throws Exception {
        runTest("""
                public record R(double val) {
                    static void main() {
                        Object o = new R(Double.NaN);
                        if (!(o instanceof R(Double.NaN))) {
                            throw new AssertionError("1");
                        }
                        if (o instanceof R(0)) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    private void runTest(String code, String... expected) throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        if (Files.exists(classes)) {
            tb.cleanDirectory(classes);
        } else {
            Files.createDirectories(classes);
        }

        new JavacTask(tb)
                .options("--enable-preview", "--release", System.getProperty("java.specification.version"))
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        List<String> log =
            new JavaTask(tb).classpath(classes.toString())
                            .vmOptions("--enable-preview")
                            .className("R")
                            .run()
                            .writeAll()
                            .getOutputLines(Task.OutputKind.STDOUT);
        List<String> expectedAsList = List.of(expected);

        if (!Objects.equals(log, expectedAsList)) {
            throw new AssertionError("Incorrect result, expected: " + expectedAsList +
                                     ", got: " + log);
        }
    }

    @Test
    public void testSourceLevelCheck() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");

        Path classes = base.resolve("classes");

        if (Files.exists(classes)) {
            tb.cleanDirectory(classes);
        } else {
            Files.createDirectories(classes);
        }

        List<String> log;
        List<String> expected;

        tb.writeJavaFiles(src,
                          """
                          public record R(int val) {
                              static void main() {
                                  Object o = new R(0);
                                  if (o instanceof R(0)) {}
                                  if (o instanceof R(0)) {}
                              }
                          }
                          """);

        log =
            new JavacTask(tb)
                    .options("--release", "25",
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Expect.FAIL)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

        expected = List.of(
                "R.java:4:28: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.constant.patterns)",
                "1 error"
        );

        if (!Objects.equals(log, expected)) {
            throw new AssertionError("Incorrect result, expected: " + expected +
                                     ", got: " + log);
        }

        tb.writeJavaFiles(src,
                          """
                          public record R(int val) {
                              static void main() {
                                  Object o = new R(0);
                                  switch (o) {
                                      case R(1) -> {}
                                      case R(2) -> {}
                                  }
                              }
                          }
                          """);

        log =
            new JavacTask(tb)
                    .options("--release", "25",
                             "-XDrawDiagnostics")
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(Expect.FAIL)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

        expected = List.of(
                "R.java:5:20: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.constant.patterns)",
                "1 error"
        );

        if (!Objects.equals(log, expected)) {
            throw new AssertionError("Incorrect result, expected: " + expected +
                                     ", got: " + log);
        }
    }

    @Test
    public void testConstantCast() throws Exception {
        runTest("""
                public record R(Number val) {
                    static void main() {
                        Object o = new R(1);
                        if (o instanceof R(2)) {
                            throw new AssertionError("0");
                        }
                        if (!(o instanceof R(1))) {
                            throw new AssertionError("1");
                        }
                        if (o instanceof R(1L)) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testConstantCastSwitch() throws Exception {
        runTest("""
                public record R(Number val) {
                    static void main() {
                        Object o = new R(1);
                        switch (o) {
                            case R(2) -> throw new AssertionError("0");
                            case R(1L) -> throw new AssertionError("1");
                            case R(1) -> System.out.println("correct");
                            default -> throw new AssertionError("2");
                        }
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testTopLevelConstant() throws Exception {
        runTest("""
                public record R(Number val) {
                    static void main() {
                        Object o = 1;
                        switch (o) {
                            case 2 -> throw new AssertionError("0");
                            case 1L -> throw new AssertionError("1");
                            case 1 -> System.out.println("correct");
                            case Object _ -> throw new AssertionError("2");
                        }
                    }
                }
                """,
                "correct");
    }

    @Test
    public void testEnumConstant1() throws Exception {
        runTest("""
                public record R(Object o) {
                    static void main() {
                        Object o = new R(E.A);
                        switch (o) {
                            case R(E.B) -> throw new AssertionError("0");
                            case R(E.A) -> System.out.println("correct");
                            case Object _ -> throw new AssertionError("2");
                        }
                    }
                }
                enum E {
                    A, B, C;
                }
                """,
                "correct");
    }

    @Test
    public void testClassConstant() throws Exception {
        runTest("""
                public record R(Object o) {
                    static void main() {
                        Object o = new R(String.class);
                        switch (o) {
                            case R(Integer.class) -> throw new AssertionError("0");
                            case R(String.class) -> System.out.println("correct");
                            case Object _ -> throw new AssertionError("2");
                        }
                    }
                }
                """,
                "correct");
    }
}
