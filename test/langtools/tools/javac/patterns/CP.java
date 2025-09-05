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
import toolbox.ToolBox;

import org.junit.Test;

public class CP {
    private ToolBox tb;

    public CP() {
        tb = new ToolBox();
    }

    @Test
    public void testPatternsInJava() throws Exception {
        Path base = Paths.get("."); //XXX: should(?) use @TempDir, but it didn't work for me
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          """
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
                          """);

        Path classes = base.resolve("classes");

        if (Files.exists(classes)) {
            tb.cleanDirectory(classes);
        } else {
            Files.createDirectories(classes);
        }

        new JavacTask(tb)
                .options("--enable-preview", "--source", System.getProperty("java.specification.version"))
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
        List<String> expected = List.of("correct");

        if (!Objects.equals(log, expected)) {
            throw new AssertionError("Incorrect result, expected: " + expected +
                                     ", got: " + log);
        }
    }

}
