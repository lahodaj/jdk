/* --------------------------------------------------------------------------------------------
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 * ------------------------------------------------------------------------------------------ */
/*Heavily influenced by the extension for Kotlin Language Server which is:
 * Copyright (c) 2016 George Fraser
 * Copyright (c) 2018 fwcd
 */
'use strict';

import { window, workspace, ExtensionContext } from 'vscode';

import {
	LanguageClient,
	LanguageClientOptions,
    ServerOptions
} from 'vscode-languageclient/node';

import * as path from 'path';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    //verify acceptable JDK is available/set:
    let specifiedJDK: string = workspace.getConfiguration('jshell-demo').get('jdkhome');

    if (specifiedJDK === null) {
        window.showErrorMessage('The JShell demo server needs a JDK to be specified under jshell-demo.jdkhome, but none specified.');
        return ;
    }
    let serverPath = path.resolve(context.extensionPath, "server");

    let serverOptions: ServerOptions;
    let args: string[] = [];
    if (specifiedJDK != null) {
        args = ['-classpath', "target/dependency/animal-sniffer-annotations-1.17.jar:target/dependency/checker-qual-2.5.2.jar:target/dependency/error_prone_annotations-2.2.0.jar:target/dependency/failureaccess-1.0.1.jar:target/dependency/gson-2.8.9.jar:target/dependency/guava-27.1-jre.jar:target/dependency/j2objc-annotations-1.1.jar:target/dependency/jsr305-3.0.2.jar:target/dependency/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar:target/dependency/org.eclipse.lsp4j-0.13.0.jar:target/dependency/org.eclipse.lsp4j.debug-0.13.0.jar:target/dependency/org.eclipse.lsp4j.generator-0.13.0.jar:target/dependency/org.eclipse.lsp4j.jsonrpc-0.13.0.jar:target/dependency/org.eclipse.lsp4j.jsonrpc.debug-0.13.0.jar:target/dependency/org.eclipse.xtend.lib-2.24.0.jar:target/dependency/org.eclipse.xtend.lib.macro-2.24.0.jar:target/dependency/org.eclipse.xtext.xbase.lib-2.24.0.jar:target/server-1.0-SNAPSHOT.jar",
                "org.openjdk.jshell.integration.server.Server"
        ];
    }
    serverOptions = {
        command: specifiedJDK + "/bin/java",
        args: args,
        options: { cwd: serverPath }
    }

    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: ['jshell'],
        synchronize: {
            fileEvents: [
                workspace.createFileSystemWatcher('**/*.jshell')
            ]
        },
        outputChannelName: 'JShell Demo',
        revealOutputChannelOn: 4 // never
    }

	// Create the language client and start the client.
	client = new LanguageClient(
		'jshell-demo',
		'JShell Demo',
		serverOptions,
		clientOptions
	);

	// Start the client. This will also launch the server
	client.start();
}

export function deactivate(): Thenable<void> {
	if (!client) {
		return undefined;
	}
	return client.stop();
}
