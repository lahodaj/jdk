/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8389033
 * @summary Verify Gen can handle certain problematic code forms gracefully.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit GenRecovery
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.*;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.ToolBox;

public class GenRecovery {

    private Path base;
    private ToolBox tb = new ToolBox();

    @Test //JDK-8389033
    public void testDeeplyNestedTryFinally() throws Exception {
        StringBuilder finallyBuilder = new StringBuilder();

        finallyBuilder.append("a = 0;");

        for (int nesting = 0; nesting < 100; nesting++) {
            finallyBuilder.insert(0, "try { a = 0; } finally {").append("}");
        }

        String code = """
                      class Test {
                          public void test() {
                              int a = 0;
                              $TRY
                          }
                      }
                      """.replace("$TRY", finallyBuilder);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(classes)
                .run(Expect.FAIL)
                .writeAll()
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:4:2365: compiler.err.limit.code.too.large.for.try.stmt",
                "1 error"
        );

        assertEquals(expected, actual);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
