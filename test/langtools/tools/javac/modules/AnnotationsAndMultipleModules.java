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
 * @summary Test annotations can be read correctly
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.classfile
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main AnnotationsAndMultipleModules
 */

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import toolbox.JavacTask;

public class AnnotationsAndMultipleModules extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        AnnotationsAndMultipleModules t = new AnnotationsAndMultipleModules();
        t.runTests();
    }

    @Test
    public void testAnnotationInDifferentModule(Path base) throws Exception {
        Path lib = base.resolve("lib");
        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");

        tb.writeJavaFiles(libSrc,
                          """
                          module lib {
                              exports lib;
                          }
                          """,
                          """
                          package lib;
                          public @interface A {
                          }
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
                .outdir(libClasses)
                .files(findJavaFiles(libSrc))
                .run()
                .writeAll();

        Path use = base.resolve("use");
        Path useSrc = use.resolve("src");
        Path useClasses = use.resolve("classes");

        tb.writeJavaFiles(useSrc,
                          """
                          module use {
                              requires lib;
                          }
                          """,
                          """
                          package use;
                          @lib.A class C {
                          }
                          """);

        Files.createDirectories(useClasses);

        new JavacTask(tb)
                .options("--module-path", libClasses.toString())
                .outdir(useClasses)
                .files(findJavaFiles(useSrc))
                .run()
                .writeAll();

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          """
                          class Test {
                          }
                          """);

        new JavacTask(tb)
                .options("--module-path", libClasses.toString() + File.pathSeparator + useClasses,
                         "--add-modules", "use")
                .files(src.resolve("Test.java"))
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() == TaskEvent.Kind.ENTER) {
                                Elements els = task.getElements();
                                TypeElement testClass = els.getTypeElement("use.C");
                                Objects.nonNull(testClass);
                                DeclaredType annType = testClass.getAnnotationMirrors().get(0).getAnnotationType();
                                assertEquals(TypeKind.DECLARED, annType.getKind());
                                TypeElement annClass = (TypeElement) annType.asElement();
                                assertEquals("lib", els.getModuleOf(annClass).getQualifiedName().toString());
                            } 
                        }
                    });
                })
                .run()
                .writeAll();

    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", got: " + actual);
        } 
    } 

}
