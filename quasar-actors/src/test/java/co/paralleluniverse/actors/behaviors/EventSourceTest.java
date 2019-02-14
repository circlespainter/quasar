/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2016, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.actors.behaviors;

import co.paralleluniverse.actors.Actor;
import co.paralleluniverse.actors.ActorRegistry;
import co.paralleluniverse.actors.LocalActor;
import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.common.util.Exceptions;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.mockito.InOrder;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author pron
 */
public final class EventSourceTest {
    @Rule
    public final TestName name = new TestName();
    @Rule
    public final TestRule watchman = TestUtil.WATCHMAN;

    @After
    public final void tearDown() {
        ActorRegistry.clear();
    }

    public EventSourceTest() {
    }

    private EventSource<String> spawnEventSource(Initializer initializer) {
        return new EventSourceActor<String>("eventsource", initializer).spawn();
    }

    private <T extends Actor<Message, V>, Message, V> T spawnActor(T actor) {
        final co.paralleluniverse.fibers.Fiber fiber = new co.paralleluniverse.fibers.Fiber<>(actor);
        fiber.setUncaughtExceptionHandler((s, e) -> {
            e.printStackTrace();
            throw Exceptions.rethrow(e);
        });
        fiber.start();
        return actor;
    }

    @Test
    public final void testInitializationAndTermination() throws Exception {
        final Initializer init = mock(Initializer.class);
        final EventSource<String> es = spawnEventSource(init);

        Thread.sleep(100);
        verify(init).init();

        es.shutdown();
        LocalActor.join(es, 100, TimeUnit.MILLISECONDS);

        verify(init).terminate(null);
    }

    @Test
    public final void testNotify() throws Exception {
        final EventHandler<String> handler1 = mock(EventHandler.class);
        final EventHandler<String> handler2 = mock(EventHandler.class);

        final EventSource<String> es = spawnEventSource(null);

        es.addHandler(handler1);
        es.addHandler(handler2);

        es.notify("hello");

        Thread.sleep(100);
        InOrder inOrder = inOrder(handler1, handler2);
        inOrder.verify(handler1).handleEvent("hello");
        inOrder.verify(handler2).handleEvent("hello");

        es.removeHandler(handler1);

        es.notify("goodbye");

        es.shutdown();
        LocalActor.join(es, 100, TimeUnit.MILLISECONDS);

        verify(handler1, never()).handleEvent("goodbye");
        verify(handler2).handleEvent("goodbye");
    }

    private static final class BlockingStringEventHandler implements EventHandler<String> {
        @Override
        public final void handleEvent(String s) throws InterruptedException {
            co.paralleluniverse.fibers.Fiber.sleep(10);
        }
    }

    @Test
    public final void testBlock() throws Exception {
        final EventHandler<String> handler1 = new BlockingStringEventHandler();
        final EventHandler<String> handler2 = new BlockingStringEventHandler();

        final EventSource<String> es = spawnEventSource(null);

        es.addHandler(handler1);
        es.addHandler(handler2);

        es.notify("hello");

        Thread.sleep(100);

        es.shutdown();
        LocalActor.join(es, 100, TimeUnit.MILLISECONDS);
    }

    @Ignore
    @Test
    public final void testExceptionThrownInHandler() throws Exception {
        final Initializer init = mock(Initializer.class);
        final EventHandler<String> handler1 = mock(EventHandler.class);
        final EventHandler<String> handler2 = mock(EventHandler.class);

        final Exception myException = new RuntimeException("haha!");
        doThrow(myException).when(handler1).handleEvent(anyString());

        final EventSource<String> es = spawnEventSource(init);

        es.addHandler(handler1);
        es.addHandler(handler2);

        es.notify("hello");

        verify(handler1).handleEvent("hello");
        verify(handler2, never()).handleEvent(anyString());

        verify(init).terminate(myException);

        LocalActor.join(es, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public final void testRegistration() throws Exception {
        final EventSource<String> es = new EventSourceActor<String>() {
            @Override
            protected final void init() {
                // Strand.sleep(1000);
                register("test1");
            }
        }.spawn();

        assertTrue(es == (EventSource) ActorRegistry.getActor("test1"));
    }
}
