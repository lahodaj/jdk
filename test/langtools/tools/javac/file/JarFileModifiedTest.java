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
 * @summary XXX
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask JarFileModifiedTest
 * @run main JarFileModifiedTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.JarTask;

public class JarFileModifiedTest {
    private final ToolBox tb = new ToolBox();
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public static void main(String... args) throws IOException {
        new JarFileModifiedTest().run();
    }

    void run() throws IOException {
        Path base = Paths.get(".");
        Path lib = base.resolve("lib");
        Files.createDirectories(lib);
        Path jar = lib.resolve("test.jar");

        compileToJar(jar,
                     """
                     //version 1
                     package lib;
                     public class Lib {
                     }
                     """);
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          import lib.Lib;
                          public class Test {
                              public String toString() {
                                  return new Lib().toString();
                              }
                          }
                          """);
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);
        try (JavaFileManager fm =
                compiler.getStandardFileManager(null, null, null)) {
            new JavacTask(tb)
                    .classpath(jar)
                    .fileManager(fm)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run()
                    .writeAll();

            //the existing file manager has (likely) the test.jar still open
            //check that after the jar was updated, a new file manager(s) will
            //use the new version:
            compileToJar(jar,
                         """
                         //version 2
                         package lib;
                         public class Lib {
                             public static String test() {
                                 return "";
                             }
                         }
                         """);
            tb.writeJavaFiles(src,
                              """
                              package test;
                              import lib.Lib;
                              public class Test {
                                  public String toString() {
                                      return Lib.test();
                                  }
                              }
                              """);

            new JavacTask(tb)
                    .classpath(jar)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .run()
                    .writeAll();
        }
    }

    void compileToJar(Path jar, String code) throws IOException {
        Path base = Paths.get(".");
        Path scratch = base.resolve("scratch");
        Path src = scratch.resolve("src");
        tb.writeJavaFiles(src, code);
        Path classes = scratch.resolve("classes");
        tb.createDirectories(classes);
        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();
        new JarTask(tb, jar)
                .baseDir(classes)
                .files(Arrays.stream(tb.findFiles("class", classes)).map(p -> classes.relativize(p).toString()).toArray(String[]::new))
                .run();
    }
}
