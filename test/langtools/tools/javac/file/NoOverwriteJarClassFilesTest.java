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
 * @bug 8338675
 * @summary Verify javac does not change .jar files on the classpath
 * @library /tools/lib /tools/javac/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask JavacTestingAbstractProcessor
 * @run junit NoOverwriteJarClassFilesTest
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.JarTask;
import toolbox.ToolBox;
import toolbox.Task;

import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*
 * If there is no source path, javac also searches the classpath for sources
 * If there is no output directory specified, javac will put classfiles next to
 * their corresponding sources.
 *
 * As a consequence, javac might have (due to implicit compilation) read source
 * from a jar on the classpath, and write the corresponding classfile back into
 * the jar file. This is problematic.
 *
 * The test here ensures that javac will not write into the jar file on the
 * classpath. The classfile will be written into the current working directory,
 * which mimics the JDK 8 behavior.
 */
public class NoOverwriteJarClassFilesTest {

    final ToolBox tb = new ToolBox();

    @Test
    public void jarFileNotModifiedOrdinaryCompilation(TestInfo testInfo) throws IOException {
        Path base = Paths.get(testInfo.getTestMethod().orElseThrow().getName());

        //Prepare a jar file with class and source file with the same FQN
        //the APIs are different for the class and source file, and the test will
        //only compile without errors against the source file. The timestamp for
        //the source file is newer than the classfile, so that javac picks it up.
        Path libSrc = base.resolve("libsrc");

        tb.writeJavaFiles(libSrc,
                          """
                          package lib;
                          public class LibClass {
                              public static final String OLD_FIELD = "This will not compile with Target";
                          }
                          """);

        Path libClasses = base.resolve("libclasses");

        Files.createDirectories(libClasses);

        new JavacTask(tb)
                .outdir(libClasses)
                .files(tb.findJavaFiles(libSrc))
                .run()
                .writeAll();

        Path libJar = base.resolve("lib.jar");

        Instant olderTime = Instant.now();
        Instant newerTime = olderTime.plusSeconds(1);

        try (OutputStream jos = Files.newOutputStream(libJar);
            JarOutputStream jar = new JarOutputStream(jos)) {
            writeEntry(jar,
                       "lib/LibClass.java",
                       """
                       package lib;
                       public class LibClass {
                           public static final String NEW_FIELD = "Only this will compile with Target";
                       }
                       """.getBytes(),
                       newerTime);
            writeEntry(jar,
                       "lib/LibClass.class",
                       Files.readAllBytes(libClasses.resolve("lib/LibClass.class")),
                       olderTime);
        }

        byte[] originalJarContents = Files.readAllBytes(libJar);

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          """
                          class TargetClass {
                              static final String VALUE = lib.LibClass.NEW_FIELD;
                          }
                          """);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        //if outdir is specified, the implict classfile is always written into it:
        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src.toAbsolutePath()))
                .classpath(libJar.toAbsolutePath())
                .run()
                .writeAll();

        assertArrayEquals(originalJarContents, Files.readAllBytes(libJar), "Jar file was modified.");
        assertTrue(Files.exists(classes.resolve("lib/LibClass.class")), "Output class file was not written to classes directory.");

        //if outdir is not specified, the implicit classfile will be written to
        //the current working directory:
        Path workingDir = base.resolve("workingdir");

        Files.createDirectories(workingDir);

        new JavacTask(tb, Task.Mode.EXEC) {
            @Override
            protected ProcessBuilder getProcessBuilder() {
                return super.getProcessBuilder().directory(workingDir.toFile());
            }
        }
                .files(tb.findJavaFiles(src.toAbsolutePath()))
                .classpath(libJar.toAbsolutePath())
                .run()
                .writeAll();

        assertArrayEquals(originalJarContents, Files.readAllBytes(libJar), "Jar file was modified.");
        assertTrue(Files.exists(workingDir.resolve("LibClass.class")), "Output class file was not written to current directory.");
    }

    @Test
    public void jarFileNotModifiedThroughAP(TestInfo testInfo) throws IOException {
        //verify APs cannot accidentally write into the jar on classpath (via originatingElements):
        Path base = Paths.get(testInfo.getTestMethod().orElseThrow().getName());

        Path libSrc = base.resolve("libsrc");

        tb.writeJavaFiles(libSrc,
                          """
                          package lib;
                          public class LibClass {
                          }
                          """);

        Path libClasses = base.resolve("libclasses");

        Files.createDirectories(libClasses);

        new JavacTask(tb)
                .outdir(libClasses)
                .files(tb.findJavaFiles(libSrc))
                .run()
                .writeAll();

        Path libJar = base.resolve("lib.jar");

        new JarTask(tb, libJar)
                .baseDir(libSrc)
                .files("lib/LibClass.java")
                .run();

        byte[] originalJarContents = Files.readAllBytes(libJar);

        Path src = base.resolve("src");

        tb.writeJavaFiles(src,
                          """
                          class TargetClass {
                              NoOverwriteJarClassFilesTestGenerated1 gen1;
                              NoOverwriteJarClassFilesTestGenerated2 gen2;
                          }
                          """);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        //if outdir is specified (and no source output), the files are written into it:
        new JavacTask(tb)
                .options("-implicit:none")
                .outdir(classes)
                .files(tb.findJavaFiles(src.toAbsolutePath()))
                .classpath(libJar.toAbsolutePath())
                .processors(new TestAnnotationProcessor())
                .run()
                .writeAll();

        assertArrayEquals(originalJarContents, Files.readAllBytes(libJar), "Jar file was modified.");
        assertTrue(Files.exists(classes.resolve("NoOverwriteJarClassFilesTestGenerated1.class")), "Output was not written to classes directory.");
        assertTrue(Files.exists(classes.resolve("NoOverwriteJarClassFilesTestGenerated2.java")), "Output was not written to classes directory.");
        assertTrue(Files.exists(classes.resolve("lib/data.txt")), "Output was not written to classes directory.");

        //if outdir is not specified, the files will be written to
        //the current working directory:
        Path workingDir = base.resolve("workingdir");

        Files.createDirectories(workingDir);

        new JavacTask(tb, Task.Mode.EXEC) {
            @Override
            protected ProcessBuilder getProcessBuilder() {
                return super.getProcessBuilder().directory(workingDir.toFile());
            }
        }
                .files(tb.findJavaFiles(src.toAbsolutePath()))
                .classpath(libJar.toAbsolutePath())
                .options("-implicit:none",
                         "-processorpath", System.getProperty("test.class.path"),
                         "-processor", TestAnnotationProcessor.class.getName())
                .run()
                .writeAll();

        assertArrayEquals(originalJarContents, Files.readAllBytes(libJar), "Jar file was modified.");
        assertTrue(Files.exists(workingDir.resolve("NoOverwriteJarClassFilesTestGenerated1.class")), "Output was not written to current directory.");
        assertTrue(Files.exists(workingDir.resolve("NoOverwriteJarClassFilesTestGenerated2.java")), "Output was not written to current directory.");
        assertTrue(Files.exists(workingDir.resolve("data.txt")), "Output was not written to classes directory.");
    }

    public static class TestAnnotationProcessor extends JavacTestingAbstractProcessor {
        private boolean generated;
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
            if (generated) {
                return false;
            }

            generated = true;

            TypeElement libType = processingEnv.getElementUtils().getTypeElement("lib.LibClass");

            try {
                JavaFileObject classFile = filer.createClassFile("NoOverwriteJarClassFilesTestGenerated1", libType);
                try (OutputStream out = classFile.openOutputStream()) {
                    out.write(NoOverwriteJarClassFilesTest.class
                                                          .getClassLoader()
                                                          .getResourceAsStream("NoOverwriteJarClassFilesTestGenerated1.class")
                                                          .readAllBytes());
                }
                assertNonJar(classFile, "/NoOverwriteJarClassFilesTestGenerated1.class");
                JavaFileObject sourceFile = filer.createSourceFile("NoOverwriteJarClassFilesTestGenerated2", libType);
                try (Writer out = sourceFile.openWriter()) {
                    out.write("""
                              public class NoOverwriteJarClassFilesTestGenerated2 {}
                              """);
                }
                assertNonJar(sourceFile, "/NoOverwriteJarClassFilesTestGenerated2.java");
                FileObject resourceFile = filer.createResource(CLASS_OUTPUT, "lib", "data.txt", libType);
                try (Writer out = resourceFile.openWriter()) {
                    out.write("""
                              text
                              """);
                }
                assertNonJar(resourceFile, "/data.txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return false;
        }

        static void assertNonJar(FileObject file, String uriSuffix) {
            URI uri = file.toUri();
            if (!"file".equals(uri.getScheme())) {
                throw new AssertionError("Unexpected scheme: " + uri.getScheme());
            }
            if (!uri.getSchemeSpecificPart().endsWith(uriSuffix)) {
                throw new AssertionError("Unexpected suffix: " + uri.getSchemeSpecificPart());
            }
        }
    }

    private static void writeEntry(JarOutputStream jar, String name, byte[] bytes, Instant timestamp) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setLastModifiedTime(FileTime.from(timestamp));
        jar.putNextEntry(e);
        jar.write(bytes);
        jar.closeEntry();
    }

}

class NoOverwriteJarClassFilesTestGenerated1 {}
