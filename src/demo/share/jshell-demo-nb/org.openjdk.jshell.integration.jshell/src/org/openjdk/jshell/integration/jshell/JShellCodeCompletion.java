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
package org.openjdk.jshell.integration.jshell;

import java.io.CharConversionException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import static javax.lang.model.element.ElementKind.BINDING_VARIABLE;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;
import static javax.lang.model.element.ElementKind.EXCEPTION_PARAMETER;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.LOCAL_VARIABLE;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.ElementKind.MODULE;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.element.ElementKind.PARAMETER;
import static javax.lang.model.element.ElementKind.RECORD_COMPONENT;
import static javax.lang.model.element.ElementKind.RESOURCE_VARIABLE;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import static javax.lang.model.type.TypeKind.DECLARED;
import javax.lang.model.type.TypeMirror;
import javax.swing.Action;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import jdk.jshell.JShell;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionContext;
import jdk.jshell.SourceCodeAnalysis.CompletionState;
import jdk.jshell.SourceCodeAnalysis.ElementSuggestion;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager;
import org.netbeans.spi.editor.completion.CompletionDocumentation;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.util.Exceptions;
import org.openide.xml.XMLUtil;

public class JShellCodeCompletion extends AsyncCompletionQuery {

    private final JTextComponent comp;

    public JShellCodeCompletion(JTextComponent comp) {
        this.comp = comp;
    }

    @Override
    protected void query(CompletionResultSet crs, Document dcmnt, int cursor) {
        try {
            String content = dcmnt.getText(0, cursor);
            computeCodeCompletion(content, cursor).forEach(crs::addItem);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            crs.finish();
        }
    }

    private List<CompletionItem> computeCodeCompletion(String content, int offset) {
        JShell jshell = JShell.create();
        SourceCodeAnalysis analysis = jshell.sourceCodeAnalysis();

        return analysis.completionSuggestions(content, offset, (state, suggestions) -> suggestions.stream().map(s -> convert(state, s, offset)).toList());
    }
 
    private CompletionItem convert(CompletionState state, ElementSuggestion suggestion, int cursor) {
        if (suggestion.keyword() != null) {
            return CompletionUtilities.newCompletionItemBuilder(suggestion.keyword())
                                      .startOffset(suggestion.anchor())
                                      .endOffset(cursor)
                                      .insertText(suggestion.keyword())
                                      .iconResource(JAVA_KEYWORD)
                                      .leftHtmlText(suggestion.keyword())
                                      .build();
        } else {
            Element el = suggestion.element();
            TypeMirror realType = switch (el.getKind()) {
                case CONSTRUCTOR, METHOD, FIELD -> state.selectorType() != null ? state.typeUtils().asMemberOf((DeclaredType) state.selectorType(), el) : el.asType();
                default -> el.asType();
            };

            String leftText = simpleName(el) + switch (el.getKind()) {
                case CONSTRUCTOR, METHOD -> {
                    if (state.completionContext().contains(CompletionContext.ANNOTATION_ATTRIBUTE)) {
                        yield " = ";
                    } else {
                        StringBuilder result = new StringBuilder();
                        result.append("(");
                        List<? extends VariableElement> params = ((ExecutableElement) el).getParameters();
                        ExecutableType mt = (ExecutableType) realType;
                        String sep = "";
                        for (int i = 0; i < params.size(); i++) {
                            result.append(sep)
                                  .append(escape(simpleName(mt.getParameterTypes().get(i))))
                                  .append(" <font color='#E0A041'>")
                                  .append(escape(params.get(i).getSimpleName()))
                                  .append("</font>");
                            sep = ", ";
                        }
                        result.append(")");
                        yield result.toString();
                    }
                }
                default -> "";
            };

            String rightText = switch (el.getKind()) {
                case METHOD -> simpleName(((ExecutableType) realType).getReturnType()).toString();
                case BINDING_VARIABLE, ENUM_CONSTANT, EXCEPTION_PARAMETER, FIELD, LOCAL_VARIABLE, PARAMETER, RECORD_COMPONENT, RESOURCE_VARIABLE -> simpleName(realType).toString();
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
                        yield "()${cursor}";
                    } else {
                        int[] i = new int[] {1};
                        yield ee.getParameters().stream().map(ve -> "${p" + i[0]++ + " default=\"" + ve.getSimpleName() + "\"}").collect(Collectors.joining(", ", "(", ")${cursor}"));
                    }
                }
                default -> "";
            };

            if (state.completionContext().contains(CompletionContext.TYPES_AS_ANNOTATIONS)) {
                insert = "@" + insert;
                leftText = "@" + leftText;
            }

            String insertFin = insert;
            String iconResource;

            if (state.completionContext().contains(CompletionContext.ANNOTATION_ATTRIBUTE)) {
                iconResource = ATTRIBUTE;
            } else {
                iconResource = KIND_TO_PATHS.getOrDefault(el.getKind(), JAVA_KEYWORD);
            }

            return CompletionUtilities.newCompletionItemBuilder(insert)
                                      .startOffset(suggestion.anchor())
                                      .endOffset(cursor)
                                      .insertText(insert)
                                      .iconResource(iconResource)
                                      .leftHtmlText(leftText)
                                      .rightHtmlText(escape(rightText))
                                      .onSelect(onSelect -> CodeTemplateManager.get(comp.getDocument()).createTemporary(insertFin).insert(comp))
                                      .documentationTask(() -> {
                                          return new CompletionTask() {
                                              @Override
                                              public void query(CompletionResultSet crs) {
                                                  String text = suggestion.documentation().get();
                                                  crs.setDocumentation(new CompletionDocumentation() {
                                                      @Override
                                                      public String getText() {
                                                          return text;
                                                      }

                                                      @Override
                                                      public URL getURL() {
                                                          return null;
                                                      }

                                                      @Override
                                                      public CompletionDocumentation resolveLink(String string) {
                                                          return null;
                                                      }

                                                      @Override
                                                      public Action getGotoSourceAction() {
                                                          return null;
                                                      }
                                                      
                                                  });
                                                  crs.finish();
                                              }

                                              @Override
                                              public void refresh(CompletionResultSet crs) {
                                              }

                                              @Override
                                              public void cancel() {
                                              }
                                          };
                                      })
                                      .build();
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
                                    .map(JShellCodeCompletion::simpleName)
                                    .collect(Collectors.joining(", ", "<", ">")));
                }

                yield result;
            }
            default -> type.toString();
        };
    }

    private static String escape(CharSequence text) {
        try {
            return XMLUtil.toAttributeValue(text.toString());
        } catch (CharConversionException ex) {
            return text.toString();
        }
    }

    private static final String JAVA_KEYWORD = "org/netbeans/modules/java/editor/resources/javakw_16.png"; //NOI18N
    private static final String ATTRIBUTE = "org/netbeans/modules/java/editor/resources/attribute_16.png"; //NOI18N

    //could also include modifiers, access flags
    private static final Map<ElementKind, String> KIND_TO_PATHS = Map.of(
            ElementKind.MODULE, "org/netbeans/modules/java/editor/resources/module.png", // NOI18N
            ElementKind.CLASS, "org/netbeans/modules/editor/resources/completion/class_16.png", //NOI18N
            ElementKind.INTERFACE, "org/netbeans/modules/editor/resources/completion/interface.png", // NOI18N
            ElementKind.ENUM, "org/netbeans/modules/editor/resources/completion/enum.png", // NOI18N
            ElementKind.RECORD, "org/netbeans/modules/editor/resources/completion/record.png", // NOI18N
            ElementKind.ANNOTATION_TYPE, "org/netbeans/modules/editor/resources/completion/annotation_type.png", // NOI18N
            ElementKind.FIELD, "org/netbeans/modules/editor/resources/completion/field_16.png", //NOI18N
            ElementKind.ENUM_CONSTANT, "org/netbeans/modules/editor/resources/completion/field_16.png", //NOI18N
            ElementKind.METHOD, "org/netbeans/modules/editor/resources/completion/method_16.png", //NOI18N
            ElementKind.CONSTRUCTOR, "org/netbeans/modules/editor/resources/completion/constructor_16.png" //NOI18N
    );

    @MimeRegistration(mimeType="text/x-jshell", service=CompletionProvider.class)
    public static final class CompletionProviderImpl implements CompletionProvider {

        @Override
        public CompletionTask createTask(int i, JTextComponent jtc) {
            return new AsyncCompletionTask(new JShellCodeCompletion(jtc), jtc);
        }

        @Override
        public int getAutoQueryTypes(JTextComponent jtc, String string) {
            return 0;
        }
        
    }

}
