/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8151754 8080883 8160089 8170162 8166581 8172102 8171343 8178023 8186708 8179856 8185840 8190383
 * @summary Testing startExCe-up options.
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build Compiler toolbox.ToolBox
 * @run junit StartOptionTest
 */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Consumer;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jdk.jshell.tool.JavaShellToolBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StartOptionTest {

    protected ByteArrayOutputStream cmdout;
    protected ByteArrayOutputStream cmderr;
    protected ByteArrayOutputStream console;
    protected ByteArrayOutputStream userout;
    protected ByteArrayOutputStream usererr;
    protected InputStream cmdInStream;

    private JavaShellToolBuilder builder() {
        // turn on logging of launch failures
        Logger.getLogger("jdk.jshell.execution").setLevel(Level.ALL);
        return JavaShellToolBuilder
                .builder()
                .out(new PrintStream(cmdout), new PrintStream(console), new PrintStream(userout))
                .err(new PrintStream(cmderr), new PrintStream(usererr))
                .in(cmdInStream, null)
                .persistence(new HashMap<>())
                .env(new HashMap<>())
                .locale(Locale.ROOT);
    }

    protected int runShell(String... args) {
        try {
            return builder()
                    .start(args);
        } catch (Exception ex) {
            Assertions.fail("Repl tool died with exception", ex);
        }
        return -1; // for compiler
    }

    protected void check(ByteArrayOutputStream str, Consumer<String> checkOut, String label) {
        byte[] bytes = str.toByteArray();
        str.reset();
        String out = new String(bytes, StandardCharsets.UTF_8);
        out = stripAnsi(out);
        out = out.replaceAll("[\r\n]+", "\n");
        if (checkOut != null) {
            checkOut.accept(out);
        } else {
            Assertions.assertEquals("", out, label + ": Expected empty -- ");
        }
    }

    protected void checkExit(int ec, Consumer<Integer> checkCode) {
        if (checkCode != null) {
            checkCode.accept(ec);
        } else {
            Assertions.assertEquals(0, ec, "Expected standard exit code (0), but found: " + ec);
        }
    }

    // Start and check the resultant: exit code (Ex), command output (Co),
    // user output (Uo), command error (Ce), and console output (Cn)
    protected void startExCoUoCeCn(Consumer<Integer> checkExitCode,
            Consumer<String> checkCmdOutput,
            Consumer<String> checkUserOutput,
            Consumer<String> checkError,
            Consumer<String> checkConsole,
            String... args) {
        int ec = runShell(args);
        checkExit(ec, checkExitCode);
        check(cmdout, checkCmdOutput, "cmdout");
        check(cmderr, checkError, "cmderr");
        check(console, checkConsole, "console");
        check(userout, checkUserOutput, "userout");
        check(usererr, null, "usererr");
    }

    // Start with an exit code and command error check
    protected void startExCe(int eec, Consumer<String> checkError, String... args) {
        StartOptionTest.this.startExCoUoCeCn((Integer ec) -> Assertions.assertEquals(eec, (int) ec,
                        "Expected error exit code (" + eec + "), but found: " + ec),
                null, null, checkError, null, args);
    }

    // Start with a command output check
    protected void startCo(Consumer<String> checkCmdOutput, String... args) {
        StartOptionTest.this.startExCoUoCeCn(null, checkCmdOutput, null, null, null, args);
    }

    private Consumer<String> assertOrNull(String expected, String label) {
        return expected == null
                ? null
                : s -> Assertions.assertEquals(expected.trim(), s.replaceAll("\\r\\n?", "\n").trim(), label);
    }

    // Start and check the resultant: exit code (Ex), command output (Co),
    // user output (Uo), command error (Ce), and console output (Cn)
    protected void startExCoUoCeCn(int expectedExitCode,
            String expectedCmdOutput,
            String expectedUserOutput,
            String expectedError,
            String expectedConsole,
            String... args) {
        startExCoUoCeCn(expectedExitCode == 0
                        ? null
                        : (Integer i) -> Assertions.assertEquals(expectedExitCode, (int) i,
                        "Expected exit code (" + expectedExitCode + "), but found: " + i),
                assertOrNull(expectedCmdOutput, "cmdout: "),
                assertOrNull(expectedUserOutput, "userout: "),
                assertOrNull(expectedError, "cmderr: "),
                assertOrNull(expectedConsole, "console: "),
                args);
    }

    // Start with an expected exit code and command error
    protected void startExCe(int ec, String expectedError, String... args) {
        startExCoUoCeCn(ec, null, null, expectedError, null, args);
    }

    // Start with an expected command output
    protected void startCo(String expectedCmdOutput, String... args) {
        startExCoUoCeCn(0, expectedCmdOutput, null, null, null, args);
    }

    // Start with an expected user output
    protected void startUo(String expectedUserOutput, String... args) {
        startExCoUoCeCn(0, null, expectedUserOutput, null, null, args);
    }

    @BeforeEach
    public void setUp() {
        cmdout = new ByteArrayOutputStream();
        cmderr = new ByteArrayOutputStream();
        console = new ByteArrayOutputStream();
        userout = new ByteArrayOutputStream();
        usererr = new ByteArrayOutputStream();
        setIn("/exit\n");
    }

    protected String writeToFile(String stuff) {
        Compiler compiler = new Compiler();
        Path p = compiler.getPath("doit.repl");
        compiler.writeToFile(p, stuff);
        return p.toString();
    }

    // Set the input from a String
    protected void setIn(String s) {
        cmdInStream = new ByteArrayInputStream(s.getBytes());
    }

    // Test load files
    @Test
    public void testCommandFile() {
        String fn = writeToFile("String str = \"Hello \"\n" +
                "/list\n" +
                "System.out.println(str + str)\n" +
                "/exit\n");
        startExCoUoCeCn(0,
                "1 : String str = \"Hello \";\n",
                "Hello Hello",
                null,
                null,
                "--no-startup", fn, "-s");
    }

    // Test that the usage message is printed
    @Test
    public void testUsage() {
        for (String opt : new String[]{"-?", "-h", "--help"}) {
            startCo(s -> {
                Assertions.assertTrue(s.split("\n").length >= 7, "Not enough usage lines: " + s);
                Assertions.assertTrue(s.startsWith("Usage:   jshell <option>..."), "Unexpect usage start: " + s);
                Assertions.assertTrue(s.contains("--show-version"), "Expected help: " + s);
                Assertions.assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
            }, opt);
        }
    }

    // Test the --help-extra message
    @Test
    public void testHelpExtra() {
        for (String opt : new String[]{"-X", "--help-extra"}) {
            startCo(s -> {
                Assertions.assertTrue(s.split("\n").length >= 5, "Not enough help-extra lines: " + s);
                Assertions.assertTrue(s.contains("--add-exports"), "Expected --add-exports: " + s);
                Assertions.assertTrue(s.contains("--execution"), "Expected --execution: " + s);
                Assertions.assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
            }, opt);
        }
    }

    // Test handling of bogus options
    @Test
    public void testUnknown() {
        startExCe(1, "Unknown option: u", "-unknown");
        startExCe(1, "Unknown option: unknown", "--unknown");
    }

    // Test that input is read with "-" and there is no extra output.
    @Test
    public void testHypenFile() {
        setIn("System.out.print(\"Hello\");\n");
        startUo("Hello", "-");
        setIn("System.out.print(\"Hello\");\n");
        startUo("Hello", "-", "-");
        String fn = writeToFile("System.out.print(\"===\");");
        setIn("System.out.print(\"Hello\");\n");
        startUo("===Hello===", fn, "-", fn);
        // check that errors go to standard error
        setIn(") Foobar");
        startExCe(0, s -> Assertions.assertTrue(s.contains("illegal start of expression"),
                "cmderr: illegal start of expression"),
                "-");
    }

    // Test that user specified exit codes are propagated
    @Test
    public void testExitCode() {
        setIn("/exit 57\n");
        startExCoUoCeCn(57, null, null, null, "-> /exit 57", "-s");
        setIn("int eight = 8\n" +
                "/exit eight + \n" +
                " eight\n");
        startExCoUoCeCn(16, null, null, null,
                "-> int eight = 8\n" +
                "-> /exit eight + \n" +
                ">>  eight",
                "-s");
    }

    // Test that non-existent load file sends output to stderr and does not startExCe (no welcome).
    @Test
    public void testUnknownLoadFile() {
        startExCe(1, "File 'UNKNOWN' for 'jshell' is not found.", "UNKNOWN");
    }

    // Test bad usage of the --startup option
    @Test
    public void testStartup() {
        String fn = writeToFile("");
        startExCe(1, "Argument to startup missing.", "--startup");
        startExCe(1, "Conflicting options: both --startup and --no-startup were used.", "--no-startup", "--startup", fn);
        startExCe(1, "Conflicting options: both --startup and --no-startup were used.", "--startup", fn, "--no-startup");
        startExCe(1, "Argument to startup missing.", "--no-startup", "--startup");
    }

    // Test an option that causes the back-end to fail is propagated
    @Test
    public void testStartupFailedOption() {
        startExCe(1, s -> Assertions.assertTrue(s.contains("Unrecognized option: -hoge-foo-bar"), "cmderr: " + s),
                "-R-hoge-foo-bar");
    }

    // Test the use of non-existant files with the --startup option
    @Test
    public void testStartupUnknown() {
        startExCe(1, "File 'UNKNOWN' for '--startup' is not found.", "--startup", "UNKNOWN");
        startExCe(1, "File 'UNKNOWN' for '--startup' is not found.", "--startup", "DEFAULT", "--startup", "UNKNOWN");
    }

    // Test bad usage of --class-path option
    @Test
    public void testClasspath() {
        for (String cp : new String[]{"--class-path"}) {
            startExCe(1, "Only one --class-path option may be used.", cp, ".", "--class-path", ".");
            startExCe(1, "Argument to class-path missing.", cp);
        }
    }

    // Test bogus module on --add-modules option
    @Test
    public void testUnknownModule() {
        startExCe(1, s -> Assertions.assertTrue(s.contains("rror") && s.contains("unKnown"), "cmderr: " + s),
                "--add-modules", "unKnown");
    }

    // Test that muliple feedback options fail
    @Test
    public void testFeedbackOptionConflict() {
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.",
                "--feedback", "concise", "--feedback", "verbose");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "concise", "-s");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "verbose", "-q");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "concise", "-v");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-v", "--feedback", "concise");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-q", "-v");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-s", "-v");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-v", "-q");
        startExCe(1, "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-q", "-s");
    }

    // Test bogus arguments to the --feedback option
    @Test
    public void testNegFeedbackOption() {
        startExCe(1, "Argument to feedback missing.", "--feedback");
        startExCe(1, "Does not match any current feedback mode: blorp -- --feedback blorp", "--feedback", "blorp");
    }

    // Test --version
    @Test
    public void testVersion() {
        startCo(s -> {
            Assertions.assertTrue(s.startsWith("jshell"), "unexpected version: " + s);
            Assertions.assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
        },
                "--version");
    }

    // Test --show-version
    @Test
    public void testShowVersion() {
        startExCoUoCeCn(null,
                s -> {
                    Assertions.assertTrue(s.startsWith("jshell"), "unexpected version: " + s);
                    Assertions.assertTrue(s.contains("Welcome"), "Expected start (but got no welcome): " + s);
                },
                null,
                null,
                s -> Assertions.assertTrue(s.trim().startsWith("jshell>"), "Expected prompt, got: " + s),
                "--show-version");
    }

    @AfterEach
    public void tearDown() {
        cmdout = null;
        cmderr = null;
        console = null;
        userout = null;
        usererr = null;
        cmdInStream = null;
    }

    private static String stripAnsi(String str) {
        if (str == null) return "";
        return ANSI_CODE_PATTERN.matcher(str).replaceAll("");
    }

    public static final Pattern ANSI_CODE_PATTERN = Pattern.compile("\033\\[[\060-\077]*[\040-\057]*[\100-\176]");
}
