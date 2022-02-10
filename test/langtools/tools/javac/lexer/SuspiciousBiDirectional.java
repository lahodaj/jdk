/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278542
 * @summary Warn about suspicious bidirectional characters
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main SuspiciousBiDirectional
 */

import java.util.Arrays;
import java.util.List;

import toolbox.JavacTask;
import toolbox.ToolBox;
import toolbox.TestRunner;
import toolbox.Task;

public class SuspiciousBiDirectional extends TestRunner {
    ToolBox tb;

    SuspiciousBiDirectional() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        var t = new SuspiciousBiDirectional();
        t.runTests();
    }

    @Test
    public void testSuspiciousBiDirectional() throws Exception {
        String code = """
                /* !1 text !2 */
                /*
                 !1 text !2
                 */
                class Test {
                    String str1 = "!1 text !2";
                    String str2 = ""\"
                                  !1 text !2""\";
                    String str3 = ""\"
                                  !1 text !2
                                  ""\";
                    char c = '!1';
                }""";
        code = code.replaceAll("!1", "" + (char) 0x2066);
        code = code.replaceAll("!2", "" + (char) 0x2069);
        List<String> output = new JavacTask(tb)
                .sources(code)
                .options("-XDrawDiagnostics")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList(
                "Test.java:1:4: compiler.warn.suspicious.bidi.control",
                "Test.java:6:20: compiler.warn.suspicious.bidi.control",
                "Test.java:8:19: compiler.warn.suspicious.bidi.control",
                "Test.java:12:15: compiler.warn.suspicious.bidi.control",
                "4 warnings");
        tb.checkEqual(expected, output);
    }

    @Test
    public void testSuspiciousBiDirectionalAll() throws Exception {
        String code = """
                /* !1 text */
                class Test {
                }""";
        for (char c : new char[] {0x061C, 0x200E, 0x200F, 0x2066,
                                  0x2067, 0x2068, 0x2069, 0x202A,
                                  0x202B, 0x202C, 0x202D, 0x202E}) {
            code = code.replaceAll("!1", "" + c);
            List<String> output = new JavacTask(tb)
                    .sources(code)
                    .options("-XDrawDiagnostics")
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);
            List<String> expected = Arrays.asList(
                    "Test.java:1:4: compiler.warn.suspicious.bidi.control",
                    "1 warning");
            tb.checkEqual(expected, output);
        }
    }
}
