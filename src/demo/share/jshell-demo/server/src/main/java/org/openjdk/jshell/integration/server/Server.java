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

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class Server {

    public static void main() throws Exception {
        LanguageServerImpl server = new LanguageServerImpl();
        Launcher<LanguageClient> serverLauncher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        ((LanguageClientAware) server).connect(serverLauncher.getRemoteProxy());
        serverLauncher.startListening();
    }

    private static class LanguageServerImpl implements LanguageServer, LanguageClientAware {

        private LanguageClient client;
        private final TextDocumentService textDocumentService = new TextDocumentService() {
            private final Map<String, String> open2Content = new HashMap<>();
            private final Map<String, Map<Long, Supplier<String>>> uri2Id2Documentation = new HashMap<>();
            private final AtomicLong completionId = new AtomicLong();
            @Override
            public void didOpen(DidOpenTextDocumentParams dotdp) {
                open2Content.put(dotdp.getTextDocument().getUri(), dotdp.getTextDocument().getText());
            }

            @Override
            public void didChange(DidChangeTextDocumentParams dctdp) {
                String uri = dctdp.getTextDocument().getUri();
                String content = open2Content.get(uri);

                record Change(int start, int end, String newText) {}

                List<Change> changes = new ArrayList<>();
                for (TextDocumentContentChangeEvent c : dctdp.getContentChanges()) {
                    changes.add(new Change(Utils.position2Offset(content, c.getRange().getStart()),
                                           Utils.position2Offset(content, c.getRange().getEnd()),
                                           c.getText()));
                }

                Collections.sort(changes, (c1, c2) -> c2.start - c1.start);
                for (Change c : changes) {
                    content = content.substring(0, c.start) + c.newText() + content.substring(c.end);
                }

                open2Content.put(uri, content);
            }

            @Override
            public void didClose(DidCloseTextDocumentParams dctdp) {
                open2Content.remove(dctdp.getTextDocument().getUri());
            }

            @Override
            public void didSave(DidSaveTextDocumentParams dstdp) {
            }
            @Override
            public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
                String uri = position.getTextDocument().getUri();
                String content = open2Content.get(uri);
                if (content == null) {
                    return CompletableFuture.completedFuture(Either.forLeft(List.of()));
                }
                List<CompletionItem> completions = JShellCompletionProvider.computeCodeCompletion(content, position.getPosition());
                Map<Long, Supplier<String>> id2Documentation = uri2Id2Documentation.computeIfAbsent(uri, _ -> new HashMap<>());
                id2Documentation.clear();
                completions.forEach(item -> {
                    if (item.getData() instanceof Supplier supp) {
                        long id = completionId.getAndIncrement();
                        id2Documentation.put(id, supp);
                        item.setData(List.of(uri, id));
                    }
                });
                return CompletableFuture.completedFuture(Either.forLeft(completions));
            }

            @Override
            public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
                if (unresolved.getData() instanceof JsonArray data && data.size() == 2) {
                    String uri = data.get(0) instanceof JsonPrimitive p ? p.getAsString() : "";
                    long id = data.get(1) instanceof JsonPrimitive p ? p.getAsLong(): -1;
                    Supplier<String> doc = uri2Id2Documentation.getOrDefault(uri, Map.of())
                                                               .get(id);

                    if (doc != null) {
                         //XXX: should convert HTML to Markdown??
                        unresolved.setDocumentation(doc.get());
                    }
                }

                return CompletableFuture.completedFuture(unresolved);
            }
        };
        private final WorkspaceService workspaceService = new WorkspaceService() {
                @Override
                public void didChangeConfiguration(DidChangeConfigurationParams dccp) {
                }
                @Override
                public void didChangeWatchedFiles(DidChangeWatchedFilesParams dcwfp) {
                }
            };

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams init) {
            ServerCapabilities capabilities = new ServerCapabilities();
            capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
            final CompletionOptions complOpts = new CompletionOptions();
            complOpts.setResolveProvider(true);
            capabilities.setCompletionProvider(complOpts);
            return CompletableFuture.completedFuture(new InitializeResult(capabilities));
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void exit() {
            System.exit(0);
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return textDocumentService;
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return workspaceService;
        }

        @Override
        public void setTrace(SetTraceParams params) {
        }

        @Override
        public void connect(LanguageClient client) {
            this.client = client;
        }
    }
}
