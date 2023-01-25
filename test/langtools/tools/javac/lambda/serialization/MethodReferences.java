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
 * @bug 8154236 8174865 8300623
 * @summary Verify corect serialization&deserialization of method references.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main MethodReferences
 */

import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import toolbox.Task;

public class MethodReferences extends TestRunner {

    protected ToolBox tb;

    MethodReferences() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        MethodReferences t = new MethodReferences();
        t.runTests();
    }

    /**
     * Run all methods annotated with @Test, and throw an exception if any
     * errors are reported..
     *
     * @throws Exception if any errors occurred
     */
    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test //JDK-8154236
    public void testSourceClassFileClash(Path base) throws Exception {
        Path src = base.resolve("src");
        String code = """
                      import java.io.*;
                        public class Test {
                            public static class B implements Serializable {
                                public void test() {}
                            }
                            public static class C1 extends B {}
                            public static class C2 extends B {}
                            public static void main(String... args) throws Exception {
                                Runnable r1 = (Runnable&Serializable) new C1()::test;
                                Runnable r2 = (Runnable&Serializable) new C2()::test;
                                ByteArrayOutputStream data = new ByteArrayOutputStream();
                                try (ObjectOutputStream oos = new ObjectOutputStream(data)) {
                                    oos.writeObject(r1);
                                    oos.writeObject(r2);
                                }
                                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data.toByteArray()))) {
                                    ((Runnable) ois.readObject()).run();
                                    ((Runnable) ois.readObject()).run();
                                }
                            }
                        }
                        """;
        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run();
    }

    @Test //JDK-8174865
    public void testMultipleInterfaces(Path base) throws Exception {
        Path src = base.resolve("src");
        String code = """
                      import java.io.*;
                      import java.util.*;
                        public class Test {
                            private static void test() {}
                            public static void main(String... args) throws Exception {
                                Runnable r1 = (Runnable&Serializable) Test::test;
                                Runnable r2 = (Runnable&Serializable&I) Test::test;
                                ByteArrayOutputStream data = new ByteArrayOutputStream();
                                try (ObjectOutputStream oos = new ObjectOutputStream(data)) {
                                    oos.writeObject(r1);
                                    oos.writeObject(r2);
                                }
                                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data.toByteArray()))) {
                                    r1 = ((Runnable) ois.readObject());
                                    r1.run();
                                    System.out.println("r1 instance of I: " + (r1 instanceof I));
                                    r2 = ((Runnable) ois.readObject());
                                    r2.run();
                                    System.out.println("r2 instance of I: " + (r2 instanceof I));
                                }
                            }
                            interface I {}
                        }
                        """;
        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        String output = new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.STDOUT);

        String expected = """
                          r1 instance of I: false
                          r2 instance of I: true
                          """;

        if (!Objects.equals(expected, output)) {
            throw new AssertionError("Unexpected output: " + output + ", expected: " + expected);
        }

    }

    @Test
    public void testDeserializeOld(Path base) throws Exception {
        Path src = base.resolve("src");
        String code = """
                      import java.io.*;
                        public class Test {
                            public static class B implements Serializable {
                                public void test() {}
                            }
                            public static class C extends B {}
                            public static void main(String... args) throws Exception {
                                Runnable r1 = (Runnable&Serializable) new C()::test;
                                String legacySerialStream =
                                    "ACED0005737200216A6176612E6C616E672E696E766F6B652E" +
                                    "53657269616C697A65644C616D6264616F61D0942C29368502" +
                                    "000A49000E696D706C4D6574686F644B696E645B000C636170" +
                                    "7475726564417267737400135B4C6A6176612F6C616E672F4F" +
                                    "626A6563743B4C000E636170747572696E67436C6173737400" +
                                    "114C6A6176612F6C616E672F436C6173733B4C001866756E63" +
                                    "74696F6E616C496E74657266616365436C6173737400124C6A" +
                                    "6176612F6C616E672F537472696E673B4C001D66756E637469" +
                                    "6F6E616C496E746572666163654D6574686F644E616D657100" +
                                    "7E00034C002266756E6374696F6E616C496E74657266616365" +
                                    "4D6574686F645369676E617475726571007E00034C0009696D" +
                                    "706C436C61737371007E00034C000E696D706C4D6574686F64" +
                                    "4E616D6571007E00034C0013696D706C4D6574686F64536967" +
                                    "6E617475726571007E00034C0016696E7374616E7469617465" +
                                    "644D6574686F645479706571007E0003787000000005757200" +
                                    "135B4C6A6176612E6C616E672E4F626A6563743B90CE589F10" +
                                    "73296C020000787000000001737200065465737424433CD782" +
                                    "DC809AEA6D0200007872000654657374244200DD6C52CFC4CB" +
                                    "9B020000787076720004546573740000000000000000000000" +
                                    "78707400126A6176612F6C616E672F52756E6E61626C657400" +
                                    "0372756E740003282956740006546573742442740004746573" +
                                    "7471007E000E71007E000E";
                                byte[] data = new byte[legacySerialStream.length() / 2];
                                for (int i = 0; i < legacySerialStream.length(); i += 2) {
                                    String hexData = legacySerialStream.substring(i, i + 2);
                                    data[i / 2] = (byte) Integer.parseInt(hexData, 16);
                                }
                                try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                                     ObjectInputStream ois = new ObjectInputStream(bais)) {
                                    ((Runnable) ois.readObject()).run();
                                }
                            }
                        }
                        """;
        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll();
    }

    @Test
    public void testSerializeOldDeserializeNew(Path base) throws Exception {
        Path src = base.resolve("src");
        String code = """
                      import java.io.*;
                        public class Test {
                            public static class B implements Serializable {
                                public void test() {}
                            }
                            public static class C extends B {}
                            public static void main(String... args) throws Exception {
                                Runnable r = (Runnable&Serializable) new C()::test;
                                if (args.length == 0) {
                                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                                    try (ObjectOutputStream oos = new ObjectOutputStream(data)) {
                                        oos.writeObject(r);
                                    }
                                    for (byte b : data.toByteArray()) {
                                        System.out.print(String.format("%02X", b));
                                    }
                                } else {
                                    String serializedStream = args[0];
                                    byte[] data = new byte[serializedStream.length() / 2];
                                    for (int i = 0; i < serializedStream.length(); i += 2) {
                                        String hexData = serializedStream.substring(i, i + 2);
                                        data[i / 2] = (byte) Integer.parseInt(hexData, 16);
                                    }
                                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                                         ObjectInputStream ois = new ObjectInputStream(bais)) {
                                        ((Runnable) ois.readObject()).run();
                                    }
                                }
                            }
                        }
                        """;
        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .options("--release", "17")
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        String serialized = new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll()
                .getOutput(Task.OutputKind.STDOUT)
                .trim();

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .classArgs(serialized)
                .run()
                .writeAll();
    }

    @Test //JDK-8300623
    public void testComplexSuperTypeMethods(Path base) throws Exception {
        Path src = base.resolve("src");
        String code =
                """
                import java.io.*;
                public class Test {
                    static interface I { public default void iMethod() {} }
                    static class B { public void bMethod() {} }
                    static class C extends B implements I, Serializable { }
                    static interface I2 { public void iaMethod(); }
                    static abstract class C2 implements I2 { }
                    static class D2 extends C2 implements Serializable { public void iaMethod() {} }
                    static abstract class B3 { public abstract void aMethod(); }
                    static abstract class C3 extends B3 { }
                    static class D3 extends C3 implements Serializable { public void aMethod() {} }
                    public static void main(String... args) throws Exception {
                        C v1 = new C();
                        C2 v2 = new D2();
                        C3 v3 = new D3();
                        Runnable r1 = (Serializable & Runnable) v1::iMethod;
                        Runnable r2 = (Serializable & Runnable) v1::bMethod;
                        Runnable r3 = (Serializable & Runnable) v2::iaMethod;
                        Runnable r4 = (Serializable & Runnable) v3::aMethod;
                        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
                             ObjectOutputStream oos = new ObjectOutputStream(os)) {
                            oos.writeObject(r1);
                            oos.writeObject(r2);
                            oos.writeObject(r3);
                            oos.writeObject(r4);
                            try (InputStream is = new ByteArrayInputStream(os.toByteArray());
                                 ObjectInputStream ois = new ObjectInputStream(is)) {
                                r1 = (Runnable) ois.readObject();
                                r2 = (Runnable) ois.readObject();
                                r3 = (Runnable) ois.readObject();
                                r4 = (Runnable) ois.readObject();
                            }
                        }
                        r1.run();
                        r2.run();
                        r3.run();
                        r4.run();
                    }
                }
                """;
        tb.writeJavaFiles(src, code);

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        new JavaTask(tb)
                .classpath(classes.toString())
                .className("Test")
                .run()
                .writeAll();
    }
}
