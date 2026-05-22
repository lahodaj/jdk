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

/*
 * @test
 * @bug 9999999
 * @summary XXX
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit BiDirectionalInferenceTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class BiDirectionalInferenceTest {

    private Path base;
    private final ToolBox tb = new ToolBox();

    @Test
    public void testInferenceInstanceof() throws Exception {
        new JavacTask(tb)
                .options("-XDshould-stop.at=FLOW")
                .sources("""
                         class C {
                             record Box<T>(T val) {}
                             static <T> Box<T> empty() { return new Box<T>(null); }
                             boolean b() {
                                 return empty() instanceof Box<String>(String s) && s.isEmpty();
                             }
                         }
                         """)
                .outdir(base)
                .run()
                .writeAll();
    }

    @Test
    public void testInferenceSwitch() throws Exception {
        new JavacTask(tb)
                .options("-XDshould-stop.at=FLOW")
                .sources("""
                         class C {
                             record Box<T>(T val) {}
                             static <T> Box<T> empty() { return new Box<T>(null); }
                             boolean b() {
                                 return switch (empty()) {
                                     case Box<String>(String s) -> s.isEmpty();
                                 };
                             }
                         }
                         """)
                .outdir(base)
                .run()
                .writeAll();
    }

    @Test
    public void testEnhancedVariableDeclaration() throws Exception {
        new JavacTask(tb)
                .options("-XDshould-stop.at=FLOW",
                         "--enable-preview",
                         "--source", System.getProperty("java.specification.version"))
                .sources("""
                         class C {
                             record Box<T>(T val) {}
                             static <T> Box<T> empty() { return new Box<T>(null); }
                             boolean b() {
                                 Box<String>(String s) = empty();
                                 return s.isEmpty();
                             }
                         }
                         """)
                .outdir(base)
                .run()
                .writeAll();
    }

    @BeforeEach
    public void setUp(TestInfo info) throws IOException {
        base = Path.of(info.getTestMethod().orElseThrow().getName());
        Files.createDirectories(base);
    }
}
//TODO: non-parameteric record extending parameteric interface?
//        new JavacTask(tb)
//                .options("-XDshould-stop.at=FLOW")
//                .sources("""
//                         class C {
//                             interface I<T> {}
//                             record Box(String val) implements I<String>{}
//                             static <T> I<T> empty() { return null; }
//                             boolean b() {
//                                 return empty() instanceof Box(String s) && s.isEmpty();
//                             }
//                         }
//                         """)
//                .outdir(base)
//                .run()
//                .writeAll();
