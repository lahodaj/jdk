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
 * @summary Check behavior of module imports.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PackageClassClash
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class PackageClassClash extends TestRunner {

    private static final String SOURCE_VERSION = System.getProperty("java.specification.version");
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new PackageClassClash().runTests();
    }

    PackageClassClash() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testPackageClashTestFromOtherModule(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path ma = src.resolve("ma");
        tb.writeJavaFiles(ma,
                          """
                          module ma {
                              exports clashing;
                          }
                          """,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """);
        Path mb = src.resolve("mb");
        tb.writeJavaFiles(mb,
                          """
                          module mb {
                              requires ma;
                          }
                          """,
                          """
                          package mb;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package mb;
                          import mb.*;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("--module-source-path", src.toString(),
                         "-Xlint:clash",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testKnownPackagesFromCurrentModule(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path ma = src.resolve("ma");
        tb.writeJavaFiles(ma,
                          """
                          module ma {
                              exports ma;
                          }
                          """,
                          """
                          package ma;
                          public class clashing {
                              public static class Cls {}
                          }
                          """);
        Path mb = src.resolve("mb");
        tb.writeJavaFiles(mb,
                          """
                          module mb {
                              requires ma;
                          }
                          """,
                          """
                          package clashing;
                          public class Cls {}
                          """,
                          """
                          package mb;
                          import ma.*;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("--module-source-path", src.toString(),
                         "-Xlint:clash",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testKnownPackagesFromImports1(Path base) throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");
        Path libSrc = lib.resolve("src");
        tb.writeJavaFiles(libSrc,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """);

        Path libClasses = lib.resolve("classes");
        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run(Task.Expect.SUCCESS)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package use;
                          import clashing.*;
                          public class Use1 {}
                          """,
                          """
                          package use;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package use;
                          import use.*;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .classpath(libClasses)
                .options("-Xlint:clash",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testKnownPackagesFromImports2(Path base) throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");
        Path libSrc = lib.resolve("src");
        tb.writeJavaFiles(libSrc,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """);

        Path libClasses = lib.resolve("classes");
        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run(Task.Expect.SUCCESS)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package use;
                          import clashing.Cls;
                          public class Use1 {}
                          """,
                          """
                          package use;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package use;
                          import use.*;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .classpath(libClasses)
                .options("-Xlint:clash",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testTransitiveModuleImport(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path ma = src.resolve("ma");
        tb.writeJavaFiles(ma,
                          """
                          module ma {
                              exports ma;
                          }
                          """,
                          """
                          package ma;
                          public class clashing {
                              public static class Cls {}
                          }
                          """);
        Path mc = src.resolve("mc");
        tb.writeJavaFiles(mc,
                          """
                          module mc {
                              requires transitive ma;
                          }
                          """);
        Path mb = src.resolve("mb");
        tb.writeJavaFiles(mb,
                          """
                          module mb {
                              requires mc;
                          }
                          """,
                          """
                          package clashing;
                          public class Cls {}
                          """,
                          """
                          package mb;
                          import module mc;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("--module-source-path", src.toString(),
                         "--enable-preview", "--release", SOURCE_VERSION,
                         "-Xlint:clash",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "- compiler.note.preview.filename: Test.java, DEFAULT",
            "- compiler.note.preview.recompile",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testSuppressWarningsClass(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """,
                          """
                          package mb;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package mb;
                          import mb.*;
                          @SuppressWarnings("clash")
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("-Xlint:clash",
                         "-XDrawDiagnostics",
                         "-Werror")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of("");

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testSuppressWarningsMethod(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """,
                          """
                          package mb;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package mb;
                          import mb.*;
                          public class Test {
                              @SuppressWarnings("clash")
                              public void test() {
                                  clashing.Cls cls;
                              }
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("-Xlint:clash",
                         "-XDrawDiagnostics",
                         "-Werror")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of("");

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testSuppressWarningsVariable(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """,
                          """
                          package mb;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package mb;
                          import mb.*;
                          public class Test {
                              @SuppressWarnings("clash")
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("-Xlint:clash",
                         "-XDrawDiagnostics",
                         "-Werror")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of("");

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

    @Test
    public void testDefaultIsOn(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package clashing;
                          public class Cls {
                          }
                          """,
                          """
                          package mb;
                          public class clashing {
                              public static class Cls {}
                          }
                          """,
                          """
                          package mb;
                          import mb.*;
                          public class Test {
                              clashing.Cls cls;
                          }
                          """);

        Path classes = current.resolve("classes");
        Files.createDirectories(classes);

        List<String> actualWarnings =
            new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedWarnings = List.of(
            "Test.java:4:5: compiler.warn.package.class.clash: clashing",
            "1 warning"
        );

        if (!Objects.equals(expectedWarnings, actualWarnings)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedWarnings +
                                      ", actual: " + actualWarnings);

        }
    }

}
