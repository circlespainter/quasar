/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2015, Parallel Universe Software Co. All rights reserved.
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
import co.paralleluniverse.actors.ActorRef;
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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author pron
 */
public class FiniteStateMachineTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    @After
    public void tearDown() {
        ActorRegistry.clear();
    }

    public FiniteStateMachineTest() {
    }

    private <T extends Actor<Message, V>, Message, V> T spawnActor(T actor) {
        co.paralleluniverse.fibers.Fiber fiber = new co.paralleluniverse.fibers.Fiber(actor);
        fiber.setUncaughtExceptionHandler((s, e) -> {
            e.printStackTrace();
            throw Exceptions.rethrow(e);
        });
        fiber.start();
        return actor;
    }

    @Test
    public void testInitializationAndTermination() throws Exception {
        final Initializer init = mock(Initializer.class);
        ActorRef<Object> a = new FiniteStateMachineActor(init).spawn();

        Thread.sleep(100);
        verify(init).init();

        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);

        verify(init).terminate(null);
    }

    @Test
    public void testStates() throws Exception {
        final AtomicBoolean success = new AtomicBoolean();

        ActorRef<Object> a = new FiniteStateMachineActor() {
            @Override
            protected Callable<Callable> initialState() {
                return () -> state1();
            }

            private Callable<Callable> state1() throws InterruptedException {
                return receive(m -> {
                    if ("a".equals(m))
                        return () -> state2();
                    return null;
                });

            }

            private Callable<Callable> state2() throws InterruptedException {
                return receive(m -> {
                    if ("b".equals(m)) {
                        success.set(true);
                        return TERMINATE;
                    }
                    return null;
                });
            }
        }.spawn();

        a.send("b");
        a.send("a");

        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);

        assertTrue(success.get());
    }

    @Ignore
    @Test
    public void testExceptionThrownInState() throws Exception {
        final Initializer init = mock(Initializer.class);
        final RuntimeException myException = new RuntimeException("haha!");

        ActorRef<Object> a = new FiniteStateMachineActor() {
            @Override
            protected Callable<Callable> initialState() {
                return () -> state1();
            }

            private Callable<Callable> state1() {
                return () -> state2();
            }

            private Callable<Callable> state2() {
                throw myException;
            }
        }.spawn();

        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);

        verify(init).terminate(myException);
    }
}
