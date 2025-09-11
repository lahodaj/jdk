/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.openjdk.jshell.integration.server;

import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.MODULE;
import static javax.lang.model.element.ElementKind.PACKAGE;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import jdk.jshell.JShell;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionContext;
import jdk.jshell.SourceCodeAnalysis.CompletionState;
import jdk.jshell.SourceCodeAnalysis.ElementSuggestion;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class JShellCompletionProvider  {

    public static List<CompletionItem> computeCodeCompletion(String content, Position position) {
        int offset = Utils.position2Offset(content, position);
        JShell jshell = JShell.create();
        SourceCodeAnalysis analysis = jshell.sourceCodeAnalysis();
        String input = content.substring(0, offset);
        boolean cont = true;
        while (cont) {
            cont = false;
            SourceCodeAnalysis.CompletionInfo completeness = analysis.analyzeCompletion(input);
            if (completeness.completeness().isComplete() && !completeness.remaining().isBlank()) {
                input = completeness.remaining();
                cont = true;
            }
        }

        return analysis.completionSuggestions(input, input.length(), (state, suggestions) -> suggestions.stream().map(s -> convert(content, state, s, offset)).toList());
    }
 
    private static CompletionItem convert(String content, CompletionState state, ElementSuggestion suggestion, int cursor) {
        if (suggestion.keyword() != null) {
            CompletionItem result = new CompletionItem(suggestion.keyword());

            result.setTextEdit(Either.forLeft(new TextEdit(new Range(Utils.offset2Position(content, suggestion.anchor()), Utils.offset2Position(content, cursor)), suggestion.keyword())));
            result.setSortText((suggestion.matchesType() ? "0" : "1") + ":" + suggestion.keyword());

            return result;
        } else {
            Element el = suggestion.element();

            String label = simpleName(el) + switch (el.getKind()) {
                case CONSTRUCTOR, METHOD -> {
                    if (state.completionContext().contains(CompletionContext.ANNOTATION_ATTRIBUTE)) {
                        yield " = ";
                    } else {
                        yield ((ExecutableElement) el).getParameters().stream().map(ve -> simpleName(ve.asType()).toString() + " " + ve.getSimpleName()).collect(Collectors.joining(", ", "(", ")"));
                    }
                }
                default -> "";
            };

            String insert = nameQualifiedAsNeeded(state, el) + switch (el.getKind()) {
                case CONSTRUCTOR, METHOD -> {
                    if (state.completionContext().contains(CompletionContext.ANNOTATION_ATTRIBUTE)) {
                        yield " = ";
                    } else if (state.completionContext().contains(CompletionContext.NO_PAREN)) {
                        yield "";
                    }
                    ExecutableElement ee = (ExecutableElement) el;
                    if (ee.getParameters().isEmpty()) {
                        yield "()$0";
                    } else {
                        int[] i = new int[] {1};
                        yield ee.getParameters().stream().map(ve -> "${" + i[0]++ + ":" + ve.getSimpleName() + "}").collect(Collectors.joining(", ", "(", ")$0"));
                    }
                }
                default -> "";
            };

            if (state.completionContext().contains(CompletionContext.TYPES_AS_ANNOTATIONS)) {
                insert = "@" + insert;
                label = "@" + label;
            }

            String type = switch (el.getKind()) {
                case METHOD -> ((ExecutableElement) el).getReturnType().toString();
                case BINDING_VARIABLE, ENUM_CONSTANT, EXCEPTION_PARAMETER, FIELD, LOCAL_VARIABLE, PARAMETER, RECORD_COMPONENT, RESOURCE_VARIABLE -> el.asType().toString();
                default -> null;
            };

            CompletionItem result = new CompletionItem(label);

            result.setInsertTextFormat(InsertTextFormat.Snippet);
            result.setTextEdit(Either.forLeft(new TextEdit(new Range(Utils.offset2Position(content, suggestion.anchor()), Utils.offset2Position(content, cursor)), insert)));
            CompletionItemLabelDetails details = new CompletionItemLabelDetails();
            details.setDescription(type);
            result.setLabelDetails(details);
            result.setData(suggestion.documentation());
            result.setSortText((suggestion.matchesType() ? "0" : "1") + ":" + suggestion.keyword());

            return result;
        }
    }

    private static CharSequence simpleName(Element el) {
        return switch (el.getKind()) {
            case MODULE, PACKAGE -> ((QualifiedNameable) el).getQualifiedName();
            case CONSTRUCTOR -> el.getEnclosingElement().getSimpleName();
            default -> el.getSimpleName();
        };
    }

    private static CharSequence nameQualifiedAsNeeded(CompletionState state, Element el) {
        if (state.completionContext().contains(CompletionContext.QUALIFIED) ||
            state.availableUsingSimpleName(el)) {
            return el.getSimpleName();
        } else if (el.getKind() == ElementKind.CLASS && (el.asType().getKind().isPrimitive() || el.asType().getKind() == TypeKind.VOID)) {
            return el.getSimpleName();
        } else if (el.getKind().isClass() || el.getKind().isInterface()) {
            return nameQualifiedAsNeeded(state, el.getEnclosingElement()) + "." + el.getSimpleName();
        } else if ((el.getKind().isField() || el.getKind() == ElementKind.METHOD) && el.getModifiers().contains(Modifier.STATIC)) {
            return nameQualifiedAsNeeded(state, el.getEnclosingElement()) + "." + el.getSimpleName();
        } else {
            return simpleName(el);
        }
    }

    private static CharSequence simpleName(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED -> {
                DeclaredType dt = (DeclaredType) type;
                StringBuilder result = new StringBuilder();
                Element clazz = dt.asElement();

                result.append(clazz.getSimpleName());
                if (!dt.getTypeArguments().isEmpty()) {
                    result.append(dt.getTypeArguments()
                                    .stream()
                                    .map(JShellCompletionProvider::simpleName)
                                    .collect(Collectors.joining(", ", "<", ">")));
                }

                yield result;
            }
            default -> type.toString();
        };
    }

}
