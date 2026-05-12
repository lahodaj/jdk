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
 * @summary Check behavior of carrier interfaces
 * @library /tools/lib
 * @modules java.logging
 *          java.sql
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @compile CarrierInterfaces.java
 * @run main CarrierInterfaces
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class CarrierInterfaces extends TestRunner {

    private static final String SOURCE_VERSION = System.getProperty("java.specification.version");
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new CarrierInterfaces().runTests();
    }

    CarrierInterfaces() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSimple(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public interface Test(String c1, int c2) {
                              public static void main(String... args) {
                                  record TestImpl(String c1, int c2) implements Test {}
                                  Object o = new TestImpl("Hello", 1);
                                  if (o instanceof Test(var v1, var v2) &&
                                      "Hello".equals(v1) && 1 == v2) {
                                      System.out.println("OK");
                                  } else {
                                      throw new IllegalStateException();
                                  }
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
                .options("-doe")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();

        var out = new JavaTask(tb)
                .classpath(classes.toString())
                .className("test.Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

        var expectedOut = List.of("OK");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);
        }
    }

    @Test
    public void testParameterizedInterface(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public interface Test<K, V>(K k, V v) {
                              public static void main(String... args) {
                                  record TestImpl<K, V>(K k, V v) implements Test<K, V> {}
                                  Test<String, Integer> o = new TestImpl("Hello", 1);
                                  if (o instanceof Test<String, Integer>(var v1, var v2) &&
                                      "Hello".equals(v1) && 1 == v2) {
                                      System.out.println("OK");
                                  } else {
                                      throw new IllegalStateException();
                                  }
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
                .options("-doe", "-XDdev")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run()
            .writeAll();

        var out = new JavaTask(tb)
                .classpath(classes.toString())
                .className("test.Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

        var expectedOut = List.of("OK");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);
        }
    }

    //TODO: test separate compilation!
}
