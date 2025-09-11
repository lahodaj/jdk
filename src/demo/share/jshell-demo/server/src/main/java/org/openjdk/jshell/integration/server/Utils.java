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

import org.eclipse.lsp4j.Position;

public class Utils  {

    public static int position2Offset(String content, Position position) {
        int line = position.getLine();
        int pos = 0;
        int lastLineStart = 0;
        while (line > 0) {
            while (pos < content.length()) {
                if (content.charAt(pos) == '\n') {
                    pos++;
                    lastLineStart = pos;
                    break;
                }
                pos++;
            }
            line--;
        }
        return lastLineStart + position.getCharacter();
    }
    
    public static Position offset2Position(String content, int offset) {
        int line = 0;
        int character = 0;
        int pos = 0;

        while (pos < offset) {
            if (content.charAt(pos) == '\n') {
                line++;
                character = 0;
            } else {
                character++;
            }
            pos++;
        }

        return new Position(line, character);
    }
}
