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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class CP {
    private final ToolBox tb;
    private Path base;

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
                        if (o instanceof R(0f)) {
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
                        if (o instanceof R(0d)) {
                            throw new AssertionError("2");
                        }
                        System.out.println("correct");
                    }
                }
                """,
                "correct");
    }

    private void runTest(String code, String... expected) throws Exception {
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

    private void compileTest(String code, String... expected) throws Exception {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        if (Files.exists(classes)) {
            tb.cleanDirectory(classes);
        } else {
            Files.createDirectories(classes);
        }

        List<String> expectedAsList = List.of(expected);
        List<String> log =
            new JavacTask(tb)
                    .options("--enable-preview", "--release", System.getProperty("java.specification.version"),
                             "-Xlint:constants",
                             "-XDrawDiagnostics")
//                             "-Xlint:constants"/*,
//                             "-XDrawDiagnostics"*/)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run(expectedAsList.stream().anyMatch(l -> l.contains("error")) ? Expect.FAIL
                                                                                    : Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

        if (!Objects.equals(log, expectedAsList)) {
            throw new AssertionError("Incorrect result, expected: " + expectedAsList +
                                     ", got: " + log);
        }
    }

    @Test
    public void testSourceLevelCheck() throws Exception {
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
        compileTest(
                """
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
                """
                R.java:4:28: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, int)
                R.java:7:30: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, int)
                R.java:10:28: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, long)
                - compiler.note.preview.filename: R.java, DEFAULT
                - compiler.note.preview.recompile
                3 errors
                """.split("\n"));
    }

    @Test
    public void testConstantCastSwitch() throws Exception {
        compileTest(
                """
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
                """
                R.java:5:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, int)
                R.java:6:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, long)
                R.java:7:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, int)
                - compiler.note.preview.filename: R.java, DEFAULT
                - compiler.note.preview.recompile
                3 errors
                """.split("\n"));
    }

    @Test
    public void testTopLevelConstant() throws Exception {
        compileTest(
                """
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
                """
                R.java:5:18: compiler.err.constant.label.not.compatible: int, java.lang.Object
                R.java:6:18: compiler.err.constant.label.not.compatible: long, java.lang.Object
                R.java:7:18: compiler.err.constant.label.not.compatible: int, java.lang.Object
                - compiler.note.preview.filename: R.java, DEFAULT
                - compiler.note.preview.recompile
                3 errors
                """.split("\n"));
    }

    @Test
    public void testEnumConstant1() throws Exception {
        runTest("""
                public record R(E o) {
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
        compileTest(
                """
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
                """
                R.java:5:20: compiler.err.prob.found.req: (compiler.misc.const.expr.req)
                R.java:6:20: compiler.err.prob.found.req: (compiler.misc.const.expr.req)
                - compiler.note.preview.filename: R.java, DEFAULT
                - compiler.note.preview.recompile
                2 errors
                """.split("\n"));
    }

    @Test
    public void testInvalidConstantNested() throws Exception {
        compileTest("""
                    public record R(int i) {
                        static void main() {
                            Object o = new R(0);
                            switch (o) {
                                case R(0 + 0) -> {}
                                case R(true ? 1 : 0) -> {}
                                case R((int) 2) -> {}
                                case Object _ -> {}
                            }
                        }
                    }
                    """,
                    "R.java:5:22: compiler.err.constant.pattern.simple.expression.only",
                    "R.java:6:25: compiler.err.constant.pattern.simple.expression.only",
                    "R.java:7:20: compiler.err.constant.pattern.simple.expression.only",
                    "- compiler.note.preview.filename: R.java, DEFAULT",
                    "- compiler.note.preview.recompile",
                    "3 errors");
        compileTest("""
                    public record R(int i) {
                        static void main() {
                            int i = 0;
                            switch ((Integer) i) {
                                case 0 + 0 -> {}
                                case true ? 1 : 0 -> {}
                                case (int) 2 -> {}
                                case Integer _ -> {}
                            }
                        }
                    }
                    """,
                    "R.java:5:20: compiler.warn.constant.pattern.simple.expression.only",
                    "R.java:6:23: compiler.warn.constant.pattern.simple.expression.only",
                    "R.java:7:18: compiler.warn.constant.pattern.simple.expression.only",
                    "3 warnings");
    }

    @Test
    public void testConstantsTopLevel() throws Exception {
        runTest("""
                public record R(int i) {
                    static void main() {
                        for (int i = 0; i < 4; i++) {
                            int r;
                            switch (i) {
                                case 0 + 0 -> r = 0;
                                case true ? 1 : 0 -> r = 1;
                                case (int) 2 -> r = 2;
                                case Object _ -> r = 3;
                            }
                            System.out.println(r);
                            r = switch ((Integer) i) {
                                case 0 + 0 -> 0;
                                case true ? 1 : 0 -> 1;
                                case (int) 2 -> 2;
                                case Integer _ -> 3;
                            };
                            System.out.println(r);
                        }
                    }
                }
                """,
                "0", "0", "1", "1", "2", "2", "3", "3");
    }

    @Test
    public void testApplicability1() throws Exception {
        compileTest("""
                    public record R(int i) {
                        public void test(R r) {
                            switch (r) {
                                case R(-Long.MAX_VALUE) -> {} //error
                                case R(-1L) -> {} //error
                                case R(-1) -> {} //no error
                                default -> {}
                            }
                        }
                    }
                    """,
                    "R.java:4:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: int, long)",
                    "R.java:5:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: int, long)",
                    "- compiler.note.preview.filename: R.java, DEFAULT",
                    "- compiler.note.preview.recompile",
                    "2 errors");
    }

    @Test
    public void testApplicability2() throws Exception {
        compileTest("""
                    public record R(int i) {
                        public void test(R r) {
                            switch (r) {
                                case R("") -> {} //error, not convertible
                                default -> {}
                            }
                        }
                    }
                    """,
                    "R.java:4:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: int, java.lang.String)",
                    "- compiler.note.preview.filename: R.java, DEFAULT",
                    "- compiler.note.preview.recompile",
                    "1 error");
    }

    @Test
    public void testApplicability3() throws Exception {
        compileTest("""
                    public record R(int i) {
                        public void test(R r, Integer c) {
                            switch (r) {
                                case R(c) -> {} //error, not constant
                                default -> {}
                            }
                        }
                    }
                    """,
                    "R.java:4:20: compiler.err.prob.found.req: (compiler.misc.const.expr.req)",
                    "- compiler.note.preview.filename: R.java, DEFAULT",
                    "- compiler.note.preview.recompile",
                    "1 error");
    }

    @Test
    public void testApplicabilityBigSample() throws Exception {
        compileTest("""
                    record Box<T>(T content){}
                    record Box_Float(Float content){}
                    record Box_float(float content){}
                    record Box_Long(Long content){}
                    record Box_long(long content){}
                    record Box_Double(Double content){}
                    record Box_double(double content){}
                    record Box_Boolean(Boolean content){}
                    record Box_boolean(boolean content){}
                    record Box_Integer(Integer content){}
                    record Box_int(int content){}
                    record Box_Byte(Byte content){}
                    record Box_byte(byte content){}
                    record Box_Number(Number content){}
                    record Box_String(String content){}
                    record Box_E(E content){}
                    record Box_Foo(Foo content){}
                    record Box_Object(Object content){}
                    enum E{ A, B, C }
                    enum F{ A, B, C }
                    class Foo {}
                    
                    static void test(Object o) {
                        switch (o) {
                            case Box_Float(42) -> {}             // Error! 42 is not applicable at Float
                            case Box_float(42) -> {}             // Error! 42 is not applicable at Float
                            case Box_Float(1e1f) -> {}           // Ok
                            case Box_float(1e1f) -> {}           // Ok
                            case Box_Long(Long.MAX_VALUE) -> {}  // Ok
                            case Box_long(Long.MAX_VALUE) -> {}  // Ok
                            case Box_Long(0) -> {}  // Error! 0 (int) is not applicable at Long
                            case Box_long(0) -> {}  // Error! 0 (int) is not applicable at long
                            case Box_Long(0L) -> {}  // Ok
                            case Box_long(0L) -> {}  // Ok
                            case Box_Double(2.) -> {}            // Ok
                            case Box_double(2.) -> {}            // Ok
                            case Box_Double(42) -> {}            // Error! 42 is not applicable at Double
                            case Box_double(42) -> {}            // Error! 42 is not applicable at double
                            case Box_Boolean(true) -> {}         // Ok
                            case Box_boolean(true) -> {}         // Ok
                            case Box_Boolean(0) -> {}         // Error! 0 (int) is not applicable at Boolean
                            case Box_boolean(0) -> {}         // Error! 0 (int) is not applicable at Boolean
                            case Box_Boolean('0') -> {}         // Error! '0' is not applicable at Boolean
                            case Box_boolean('0') -> {}         // Error! '0' is not applicable at Boolean
                            case Box_Integer(42) -> {}           // Ok
                            case Box_int(42) -> {}           // Ok
                            case Box_Byte(42) -> {}              // Ok. 42 is assignment compatible with Byte
                            case Box_byte(42) -> {}              // Ok. 42 is assignment compatible with Byte
                            case Box_Byte(128) -> {}             // Error! 128 is not assignment compatible with Byte
                            case Box_byte(128) -> {}             // Error! 128 is not assignment compatible with Byte
                            case Box_Number(128) -> {}           // Error! 128 (int) is not applicable at Number
                            case Box_Number(128L) -> {}           // Error! 128 (long) is not applicable at Number
                            case Box_Number(128f) -> {}           // Error! 128 (float) is not applicable at Number
                            case Box_Number(128d) -> {}           // Error! 128 (double) is not applicable at Number
                            case Box_String("hello") -> {}       // Ok
                            case Box_E(E.A) -> {}                // Ok
                            case Box_E(F.A) -> {}                // Error! wrong type
                            case Box_Object(E.A) -> {}           // Ok
                            // null
                            case Box_E(null) -> {}               // Ok
                            case Box_Integer(null) -> {}         // Ok
                            case Box_String(null) -> {}          // Ok
                            case Box_Long(null) -> {}            // Ok
                            case Box_long(null) -> {}            // Error! not reference
                            case Box_Foo(null) -> {}             // Ok
                            case Box_Object(null) -> {}          // Ok
                            default -> {}
                        }
                    }
                    void main() {}
                    """,
                    """
                    Box.java:25:24: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Float, int)
                    Box.java:26:24: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: float, int)
                    Box.java:31:23: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Long, int)
                    Box.java:32:23: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: long, int)
                    Box.java:37:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Double, int)
                    Box.java:38:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: double, int)
                    Box.java:41:26: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Boolean, int)
                    Box.java:42:26: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: boolean, int)
                    Box.java:43:26: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Boolean, char)
                    Box.java:44:26: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: boolean, char)
                    Box.java:49:23: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Byte, int)
                    Box.java:50:23: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: byte, int)
                    Box.java:51:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, int)
                    Box.java:52:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, long)
                    Box.java:53:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, float)
                    Box.java:54:25: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: java.lang.Number, double)
                    Box.java:57:20: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: Box.E, Box.F)
                    Box.java:64:23: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: long, compiler.misc.type.null)
                    - compiler.note.preview.filename: Box.java, DEFAULT
                    - compiler.note.preview.recompile
                    18 errors
                    """.split("\n"));
    }

    @Test
    public void testNullHandling() throws Exception {
        runTest("""
                public record R(String str, Object obj) {
                    static void test(R r) {
                        switch (r) {
                            case R(null, null) -> System.out.println("null, null");
                            case R(var str, null) -> System.out.println(str + ", null");
                            case R(null, var obj) -> System.out.println("null, " + obj);
                            case R(var str, var obj) -> System.out.println(str + ", " + obj);
                            default -> System.out.println("wrong");
                        }
                    }
                    static void main() {
                        test(new R(null, null));
                        test(new R("str", null));
                        test(new R(null, 1));
                        test(new R("str", 1));
                    }
                }
                """,
                "null, null",
                "str, null",
                "null, 1",
                "str, 1");
    }

    //TODO: no .class
    //TODO: better tests .equals semantics for Float/Double?
    //TODO: no constant patterns in instanceof(!)

    @BeforeEach
    void setPath(TestInfo info) {
        base = Path.of(info.getTestMethod().orElseThrow().getName());
    }
}
