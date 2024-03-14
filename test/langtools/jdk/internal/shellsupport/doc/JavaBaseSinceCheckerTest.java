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

///*
// * @test
// * @bug 8131019 8189778 8190552
// * @summary Test JavaBaseSinceCheckerTest
// * @library /tools/lib
// * @modules jdk.compiler/com.sun.tools.javac.api
// *          jdk.compiler/com.sun.tools.javac.util
// *          jdk.compiler/com.sun.tools.javac.main
// *          jdk.compiler/com.sun.tools.javac.code
// *          jdk.compiler/jdk.internal.shellsupport.doc
// * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
// * @run junit JavaBaseSinceCheckerTest
// */


public class JavaBaseSinceCheckerTest {
    public static SinceCheckerHelper sinceCheckerTestHelper = new SinceCheckerHelper();
    //69 modules
    // must remove 19
//    @ParameterizedTest
//    @ValueSource(strings = {
//            "java.base"
//            , "jdk.internal.ed",
//            "java.compiler"
//            , "jdk.internal.jvmstat",
//            "java.datatransfer", "jdk.internal.le", "java.desktop", "jdk.internal.opt",
//            "java.instrument", "jdk.internal.vm.ci", "java.logging", "jdk.jartool",
//            "java.management", "jdk.javadoc", "java.management.rmi", "jdk.jcmd",
//            "java.naming", "jdk.jconsole", "java.net.http", "jdk.jdeps",
//            "java.prefs", "jdk.jdi", "java.rmi", "jdk.jdwp.agent",
//            "java.scripting", "jdk.jfr", "java.se", "jdk.jlink",
//            "java.security.jgss", "jdk.jpackage", "java.security.sasl", "jdk.jshell",
//            "java.smartcardio", "jdk.jsobject", "java.sql", "jdk.jstatd",
//            "java.sql.rowset", "jdk.localedata", "java.transaction.xa", "jdk.management",
//            "java.xml", "jdk.management.agent", "java.xml.crypto", "jdk.management.jfr",
//            "jdk.accessibility", "jdk.naming.dns", "jdk.attach", "jdk.naming.rmi",
//            "jdk.charsets", "jdk.net", "jdk.compiler", "jdk.nio.mapmode",
//            "jdk.crypto.cryptoki", "jdk.random", "jdk.crypto.ec", "jdk.sctp",
//            "jdk.dynalink", "jdk.security.auth", "jdk.editpad", "jdk.security.jgss",
//            "jdk.graal.compiler", "jdk.unsupported", "jdk.graal.compiler.management",
//            "jdk.unsupported.desktop", "jdk.hotspot.agent", "jdk.xml.dom", "jdk.httpserver",
//            "jdk.zipfs", "jdk.incubator.vector"
//    })


    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("No module specified. Exiting...");
            System.exit(1);
        }
        sinceCheckerTestHelper.testThisModule(args[0]);
    }
}
