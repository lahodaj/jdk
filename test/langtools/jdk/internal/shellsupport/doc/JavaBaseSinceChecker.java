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

import jdk.internal.shellsupport.doc.SinceCheckerTestHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import jdk.internal.shellsupport.doc.JavadocHelper;

import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.Runtime.Version;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class JavaBaseSinceChecker {

    //these are methods that were preview in JDK 13 and JDK 14, before the introduction
    //of the @PreviewFeature
    static final Set<String> LEGACY_PREVIEW_METHODS = Set.of(
            "method:java.lang.String:stripIndent:()",
            "method:java.lang.String:translateEscapes:()",
            "method:java.lang.String:formatted:(java.lang.Object[])"
    );

    static Map<String, IntroducedIn> classDictionary = new HashMap<>();

    SinceCheckerTestHelper sinceCheckerTestHelper ;

    @Test
    public void checkAllSinceTags() throws IOException {
        String sourcePath = "args[0]";
        String outputPath = "args[1]";
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        sinceCheckerTestHelper = new SinceCheckerTestHelper();
        for (int i = 9; i <= 23; i++) {
            try {
                JavacTask ct =
                        (JavacTask)
                                tool.getTask(
                                        null,
                                        null,
                                        null,
                                        List.of("--release", String.valueOf(i)),
                                        null,
                                        Collections.singletonList(new JavaSource()));
                ct.analyze();

                String version = String.valueOf(i);
                ct.getElements().getAllModuleElements().forEach(me -> sinceCheckerTestHelper.processModuleRecord(me, version, ct));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        JavacTask ct =
                (JavacTask)
                        tool.getTask(
                                null,
                                tool.getStandardFileManager(null, null, null), //XXX: close!
                                null,
                                List.of(
                                        "--limit-modules",
                                        "jdk.base",
                                        "-d",
                                        outputPath),
                                null,
                                Collections.singletonList(new JavaSource()));
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
        ct.getElements().getAllModuleElements().stream()
                .forEach(me -> sinceCheckerTestHelper.processModuleCheck(me,null, ct, sources));

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

    public static class IntroducedIn {
        public String introducedPreview;
        public String introducedStable;
    }
}