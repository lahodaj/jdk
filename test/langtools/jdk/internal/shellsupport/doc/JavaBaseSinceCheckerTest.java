/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8131019 8189778 8190552
 * @summary Test JavadocHelper
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/jdk.internal.shellsupport.doc
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @run testng/timeout=900/othervm -Xmx1024m JavadocHelperTest
 */

import com.sun.source.util.JavacTask;
import jdk.internal.shellsupport.doc.SinceCheckerHelper;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaBaseSinceCheckerTest {
    public static String moduleToTest = "java.base";
    //these are methods that were preview in JDK 13 and JDK 14, before the introduction
    //of the @PreviewFeature
    public SinceCheckerHelper sinceCheckerTestHelper;

    @Test
    public void testModule() throws IOException {
        sinceCheckerTestHelper = new SinceCheckerHelper();
        // source path is the path to the source code of the JDK
        // output path is the path to the output directory
        // this is temporary
        String sourcePath = "";
        String outputPath = "";

        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        for (int i = 9; i <= 23; i++) {
            try {
                JavacTask ct = (JavacTask) tool.getTask(null, null, null, List.of("--release", String.valueOf(i)), null, Collections.singletonList(new JavaSource()));
                ct.analyze();

                String version = String.valueOf(i);
                ct.getElements().getAllModuleElements().forEach(me -> sinceCheckerTestHelper.processModuleRecord(me, version, ct));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        JavacTask ct = (JavacTask) tool.getTask(null, tool.getStandardFileManager(null, null, null), //XXX: close!
                null, List.of("--limit-modules", moduleToTest, "-d", outputPath), null, Collections.singletonList(new JavaSource()));
        ct.analyze();

        Path sourcesRoot = Paths.get(sourcePath);
        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourcesRoot)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    sources.add(p);
                }
            }
        }
        ct.getElements().getAllModuleElements().stream().forEach(me -> sinceCheckerTestHelper.processModuleCheck(me, ct, sources));

    }


    private static class JavaSource extends SimpleJavaFileObject {
        private static final String TEXT = "";

        public JavaSource() {
            super(URI.create("myfo:/Test.java"), Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return TEXT;
        }
    }


}