/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Shutdown tests
 * @build KullaTesting TestingInputStream
 * @run junit ShutdownTest
 */

import java.util.function.Consumer;

import jdk.jshell.JShell;
import jdk.jshell.JShell.Subscription;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ShutdownTest extends KullaTesting {

    int shutdownCount;

    void shutdownCounter(JShell state) {
        ++shutdownCount;
    }

    @Test() 
    @Disabled
    public void testExit() {
        shutdownCount = 0;
        getState().onShutdown(this::shutdownCounter);
        assertEval("System.exit(1);");
        Assertions.assertEquals(1, shutdownCount);
    }

    @Test
    public void testCloseCallback() {
        shutdownCount = 0;
        getState().onShutdown(this::shutdownCounter);
        getState().close();
        Assertions.assertEquals(1, shutdownCount);
    }

    @Test
    public void testCloseUnsubscribe() {
        shutdownCount = 0;
        Subscription token = getState().onShutdown(this::shutdownCounter);
        getState().unsubscribe(token);
        getState().close();
        Assertions.assertEquals(0, shutdownCount);
    }

    @Test
    public void testTwoShutdownListeners() {
        ShutdownListener listener1 = new ShutdownListener();
        ShutdownListener listener2 = new ShutdownListener();
        Subscription subscription1 = getState().onShutdown(listener1);
        Subscription subscription2 = getState().onShutdown(listener2);
        getState().unsubscribe(subscription1);
        getState().close();

        Assertions.assertEquals(0, listener1.getEvents(), "Checking got events");
        Assertions.assertEquals(1, listener2.getEvents(), "Checking got events");

        getState().close();

        Assertions.assertEquals(0, listener1.getEvents(), "Checking got events");
        Assertions.assertEquals(1, listener2.getEvents(), "Checking got events");

        getState().unsubscribe(subscription2);
    }

    @Test()
    public void testCloseException() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            getState().close();
            getState().eval("45");
        });
    }

    @Test() 
    @Disabled
    public void testShutdownException() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            assertEval("System.exit(0);");
            getState().eval("45");
        });
    }

    @Test()
    public void testNullCallback() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            getState().onShutdown(null);
        });
    }

    @Test()
    public void testSubscriptionAfterClose() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            getState().close();
            getState().onShutdown(e -> {});
        });
    }

    @Test() 
    @Disabled
    public void testSubscriptionAfterShutdown() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            assertEval("System.exit(0);");
            getState().onShutdown(e -> {});
        });
    }

    private static class ShutdownListener implements Consumer<JShell> {
        private int count;

        @Override
        public void accept(JShell shell) {
            ++count;
        }

        public int getEvents() {
            return count;
        }
    }
}
