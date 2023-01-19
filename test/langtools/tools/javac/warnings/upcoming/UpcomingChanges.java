/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 9999999
 * @summary XXX
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 * @modules jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @compile UpcomingChanges.java
 * @run main UpcomingChanges
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavacTask;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;


//should be filtered out:
//adding upcoming changes: java/lang/ExceptionInInitializerError.getCause, firstDeprecated=null, firstDeprecatedForRemoval=null, firstRemoved=C
//adding upcoming changes: java/lang/Class.permittedSubclasses, firstDeprecated=null, firstDeprecatedForRemoval=null, firstRemoved=G
//adding upcoming changes: java/io/FileInputStream.finalize, firstDeprecated=9, firstDeprecatedForRemoval=A, firstRemoved=C
public class UpcomingChanges extends TestRunner {
    public static void main(String... args) throws Exception {
        UpcomingChanges r = new UpcomingChanges();
        r.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    public UpcomingChanges() throws IOException {
        super(System.err);
    }

    @Test
    public void testDeprecatedForRemoval(Path base) throws IOException {
        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Test {
                              private SecurityManager sm;
                              private void test(Thread t) {
                                  t.stop(null);
                              }
                          }
                          """);

        Path classes = base.resolve("classes");

        var out = new JavacTask(tb)
                .options("--release", "8",
                         "-d", classes.toString())
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);
        System.err.println("out: " + out);
    }

}
