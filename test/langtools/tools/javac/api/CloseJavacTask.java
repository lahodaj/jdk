/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8248449
 * @summary Verify that JavacTask.close works.
 * @modules jdk.compiler/com.sun.tools.javac.platform:open
 * @run main CloseJavacTask
 */

import com.sun.source.util.JavacTask;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static javax.tools.JavaFileObject.Kind.SOURCE;

public class CloseJavacTask {

    public static void main(String[] args) throws Exception {
        new CloseJavacTask().test();
    }

    void test() throws Exception {
        AtomicBoolean classLoaderClose = new AtomicBoolean();
        class TestFO extends SimpleJavaFileObject {
            TestFO() {
                super(URI.create("myfo:///TestFO.java"), SOURCE);
            }
            @Override
            public String getCharContent(boolean ignoreEncodingErrors) {
                return "class TestFO {}";
            }
        }
        class TestCL extends ClassLoader implements Closeable {

            public TestCL(ClassLoader parent) {
                super(parent);
            }

            @Override
            public void close() throws IOException {
                classLoaderClose.set(true);
            }

        }
        AtomicBoolean testJFMClose = new AtomicBoolean();
        class TestJFM extends ForwardingJavaFileManager<JavaFileManager> {

            public TestJFM(JavaFileManager fileManager) {
                super(fileManager);
            }

            @Override
            public ClassLoader getClassLoader(Location location) {
                return new TestCL(super.getClassLoader(location));
            }


            @Override
            public void close() throws IOException {
                testJFMClose.set(true);
                super.close();
            }
        }
        JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager sjfm = tool.getStandardFileManager(null, null, null);
             JavaFileManager testFM = new TestJFM(sjfm)) {
            try (JavacTask task = (JavacTask) tool.getTask(null, testFM, null, null,
                                                           null, List.of(new TestFO()))) {
                task.analyze();
            }
            if (!classLoaderClose.get()) {
                throw new AssertionError("Didn't close the provided ClassLoader!");
            }
            if (testJFMClose.get()) {
                throw new AssertionError("Should not close custom JavaFileManager!");
            }
            AtomicBoolean jdkPlatformProviderClose = new AtomicBoolean();
            Class<?> jdkPlatformProvider =
                    Class.forName("com.sun.tools.javac.platform.JDKPlatformProvider");
            Field callback = jdkPlatformProvider.getDeclaredField("FILE_MANAGER_CLOSE_CALLBACK");
            callback.setAccessible(true);
            callback.set(null, (Runnable) () -> jdkPlatformProviderClose.set(true));
            try (JavacTask task = (JavacTask) tool.getTask(null, null, null, List.of("--release", "11"),
                                                           null, List.of(new TestFO()))) {
                task.analyze();
            }
            if (!jdkPlatformProviderClose.get()) {
                throw new AssertionError("Didn't close the --release JavaFileManager!");
            }
            try (JavacTask task = (JavacTask) tool.getTask(null, testFM, null, List.of("--release", "11"),
                                                           null, List.of(new TestFO()))) {
                task.analyze();
            }
            if (testJFMClose.get()) {
                throw new AssertionError("Should not close custom JavaFileManager!");
            }
        }
    }

}
