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

import co.paralleluniverse.actors.*;
import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.common.util.Exceptions;
import co.paralleluniverse.fibers.DefaultFiberFactory;
import co.paralleluniverse.fibers.DefaultFiberScheduler;
import co.paralleluniverse.fibers.FiberFactory;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.Channels;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * These tests are also good tests for sendSync, as they test sendSync (and receive) from both fibers and threads.
 *
 * @author pron
 */
public class ProxyServerTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    @After
    public void tearDown() {
        ActorRegistry.clear();
        scheduler.shutdown();
    }

    static final MailboxConfig mailboxConfig = new MailboxConfig(10, Channels.OverflowPolicy.THROW);
    private ExecutorService scheduler;
    private FiberFactory factory;

    public ProxyServerTest() {
        factory = DefaultFiberFactory.getInstance();
        scheduler = DefaultFiberScheduler.getInstance();
    }

    private Server<?, ?, ?> spawnServer(boolean callOnVoidMethods, Object target) {
        return new ProxyServerActor("server", callOnVoidMethods, target).spawn(factory);
    }

    private <T extends Actor<Message, V>, Message, V> T spawnActor(T actor) {
        co.paralleluniverse.fibers.Fiber fiber = new co.paralleluniverse.fibers.Fiber(scheduler, actor);
        fiber.setUncaughtExceptionHandler((s, e) -> {
            e.printStackTrace();
            throw Exceptions.rethrow(e);
        });
        fiber.start();
        return actor;
    }

    public static interface A {
        int foo(String str, int x); // throws SuspendExecution;

        void bar(int x); // throws SuspendExecution;
    }

    @Test
    public void testShutdown() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                return str.length() + x;
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void whenCalledThenResultIsReturned() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                try {
                    Strand.sleep(50);
                    return str.length() + x;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        Actor<?, Integer> actor = spawnActor(new BasicActor<>(mailboxConfig) {
            protected Integer doRun() {
                return ((A) a).foo("hello", 2);
            }
        });

        int res = actor.get();
        assertThat(res, is(7));

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void whenCalledFromThreadThenResultIsReturned() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                try {
                    Strand.sleep(50);
                    return str.length() + x;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        int res = ((A) a).foo("hello", 2);
        assertThat(res, is(7));

        ((A) a).bar(3);

        res = ((A) a).foo("hello", 2);
        assertThat(res, is(7));

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void whenHandleCallThrowsExceptionThenItPropagatesToCaller() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                throw new RuntimeException("my exception");
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        Actor<?, Void> actor = spawnActor(new BasicActor<>(mailboxConfig) {
            protected Void doRun() {
                try {
                    int res = ((A) a).foo("hello", 2);
                    fail();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    assertThat(e.getMessage(), equalTo("my exception"));
                }
                return null;
            }
        });

        actor.join();
        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void whenHandleCallThrowsExceptionThenItPropagatesToThreadCaller() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                throw new RuntimeException("my exception");
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        try {
            int res = ((A) a).foo("hello", 2);
            fail();
        } catch (RuntimeException e) {
            e.printStackTrace();
            assertThat(e.getMessage(), equalTo("my exception"));
        }

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCast() throws Exception {
        final AtomicInteger called = new AtomicInteger(0);
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                throw new UnsupportedOperationException();
            }

            public void bar(int x) {
                try {
                    Strand.sleep(100);
                    called.set(x);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        ((A) a).bar(15);
        assertThat(called.get(), is(0));
        Thread.sleep(200);
        assertThat(called.get(), is(15));

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCallOnVoidMethod() throws Exception {
        final AtomicInteger called = new AtomicInteger(0);
        final Server<?, ?, ?> a = spawnServer(true, new A() {
            public int foo(String str, int x) {
                throw new UnsupportedOperationException();
            }

            public void bar(int x) {
                try {
                    Strand.sleep(100);
                    called.set(x);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        ((A) a).bar(15);
        assertThat(called.get(), is(15));

        a.shutdown();
        LocalActor.join(a, 100, TimeUnit.MILLISECONDS);
    }

    @Test
    public void whenCalledAndTimeoutThenThrowTimeout() throws Exception {
        final Server<?, ?, ?> a = spawnServer(false, new A() {
            public int foo(String str, int x) {
                try {
                    Strand.sleep(100);
                    return str.length() + x;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            public void bar(int x) {
                throw new UnsupportedOperationException();
            }
        });

        a.setDefaultTimeout(50, TimeUnit.MILLISECONDS);

        try {
            int res = ((A) a).foo("hello", 2);
            fail("res: " + res);
        } catch (RuntimeException e) {
            assertThat(e.getCause(), instanceOf(TimeoutException.class));
        }

        a.setDefaultTimeout(200, TimeUnit.MILLISECONDS);

        int res = ((A) a).foo("hello", 2);
        assertThat(res, is(7));

        a.shutdown();
        LocalActor.join(a, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testRegistration() throws Exception {
        final Server<?, ?, ?> a = new ProxyServerActor("test1", false, new A() {
            public int foo(String str, int x) {
                throw new UnsupportedOperationException();
            }

            public void bar(int x) {
            }
        }) {

            @Override
            protected void init() {
                register();
            }

        }.spawn();

        assertTrue(a == ActorRegistry.getActor("test1"));
    }
}
