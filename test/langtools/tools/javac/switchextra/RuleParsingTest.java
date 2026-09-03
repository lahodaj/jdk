/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8206986
 * @summary Ensure rule cases can be parsed correctly for complex expressions.
 * @modules jdk.compiler
 */

import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.*;

import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

public class RuleParsingTest {

    public static void main(String[] args) throws Exception {
        String sourceVersion = Integer.toString(Runtime.version().feature());
        new RuleParsingTest().testParseComplexExpressions(sourceVersion);
    }

    void testParseComplexExpressions(String sourceVersion) throws Exception {
        enum Type {
            CONSTANT,
            PATTERN
        }
        record Input(String label, Type type) {}
        Input[] inputs = {
            new Input("(a)", Type.CONSTANT),
            new Input("a", Type.CONSTANT),
            new Input("a + a", Type.CONSTANT),
            new Input("~a + a", Type.CONSTANT),
            new Input("a = a", Type.CONSTANT),
            new Input("a += a", Type.CONSTANT),
            new Input("a + (a)", Type.CONSTANT),
            new Input("a + (a) b", Type.CONSTANT),
            new Input("true ? a : b", Type.CONSTANT),
            new Input("m(() -> {})", Type.PATTERN),
            new Input("m(() -> 1)", Type.PATTERN),
            new Input("m(a -> 1)", Type.PATTERN),
            new Input("m((t a) -> 1)", Type.PATTERN),
        };
        record Span(long start, long end, Type type) {}
        StringBuilder code = new StringBuilder();
        List<Span> spans = new ArrayList<>();
        code.append("class Test {\n" +
                    "    void t(int i) {\n");
        for (boolean switchExpr : new boolean[] {false, true}) {
            if (switchExpr) {
                code.append("         int j = switch(i) {\n");
            } else {
                code.append("         switch(i) {\n");
            }
            for (Input input : inputs) {
                code.append("case ");
                int start = code.length();
                code.append(input.label());
                spans.add(new Span(start, code.length(), input.type()));
                code.append(" -> {}");
            }
            code.append("         };\n");
        }
        code.append("    }\n" +
                    "}\n");
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        assert tool != null;
        DiagnosticListener<JavaFileObject> noErrors = d -> {
            throw new AssertionError(d.getMessage(null));
        };

        String version = System.getProperty("java.specification.version");
        StringWriter out = new StringWriter();
        JavacTask ct = (JavacTask) tool.getTask(out, null, noErrors,
            List.of("--release", version, "--enable-preview"), null,
            Arrays.asList(new MyFileObject(code.toString())));
        CompilationUnitTree cut = ct.parse().iterator().next();
        Trees trees = Trees.instance(ct);
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitCase(CaseTree node, Void p) {
                Span currentSpan;
                if (node.getExpression() != null) {
                    long start = trees.getSourcePositions().getStartPosition(cut, node.getExpression());
                    long end = trees.getSourcePositions().getEndPosition(cut, node.getExpression());
                    currentSpan = new Span(start, end, Type.CONSTANT);
                } else {
                    PatternCaseLabelTree patternCase = (PatternCaseLabelTree) node.getLabels().getFirst();
                    long start = trees.getSourcePositions().getStartPosition(cut, patternCase.getPattern());
                    long end = trees.getSourcePositions().getEndPosition(cut, patternCase.getPattern());
                    currentSpan = new Span(start, end, Type.PATTERN /*XXX*/);
                }
                if (!spans.remove(currentSpan)) {
                    throw new AssertionError("Did not find an expression span in expected spans: " +
                                             currentSpan +
                                             " '" + node.toString() + "'");
                }
                return super.visitCase(node, p);
            }
        }.scan(cut, null);

        if (!spans.isEmpty()) {
            throw new AssertionError("Remaning spans: " + spans);
        }
    }

    static class MyFileObject extends SimpleJavaFileObject {
        private String text;

        public MyFileObject(String text) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
