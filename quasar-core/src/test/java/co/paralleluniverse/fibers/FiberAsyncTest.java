/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
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
package co.paralleluniverse.fibers;

import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.common.util.CheckedCallable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

/**
 *
 * @author pron
 */
public class FiberAsyncTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private ExecutorService scheduler;

    public FiberAsyncTest() {
        scheduler = Executors.newWorkStealingPool();
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    interface MyCallback {
        void call(String str);

        void fail(RuntimeException e);
    }

    interface Service {
        void registerCallback(MyCallback callback);
    }

    private final Service syncService = callback -> callback.call("sync result!");
    private final Service badSyncService = callback -> callback.fail(new RuntimeException("sync exception!"));
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final Service asyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(20);
            callback.call("async result!");
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });
    private final Service longAsyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(2000);
            callback.call("async result!");
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });
    private final Service badAsyncService = callback -> executor.submit(() -> {
        try {
            Thread.sleep(20);
            callback.fail(new RuntimeException("async exception!"));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    });

    private static String callService(final Service service) throws InterruptedException {
        return new MyFiberAsync() {
            @Override
            protected void requestAsync() {
                service.registerCallback(this);
            }
        }.run();
    }

    private static String callService(final Service service, long timeout) throws InterruptedException, TimeoutException {
        return new MyFiberAsync() {
            @Override
            protected void requestAsync() {
                service.registerCallback(this);
            }
        }.run(timeout, TimeUnit.MILLISECONDS);
    }

    static abstract class MyFiberAsync extends FiberAsync<String, RuntimeException> implements MyCallback {
        @Override
        public void call(String str) {
            super.asyncCompleted(str);
        }

        @Override
        public void fail(RuntimeException e) {
            super.asyncFailed(e);
        }
    }

    @Test
    public void testSyncCallback() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            final String res = callService(syncService);
            assertThat(res, equalTo("sync result!"));
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testSyncCallbackException() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(badSyncService);
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage(), equalTo("sync exception!"));
            }
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallback() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            final String res = callService(asyncService);
            assertThat(res, equalTo("async result!"));
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallbackException() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(badAsyncService);
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage(), equalTo("async exception!"));
            }
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testAsyncCallbackExceptionInRequestAsync() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                new FiberAsync<String, RuntimeException>() {
                    @Override
                    protected void requestAsync() {
                        throw new RuntimeException("requestAsync exception!");
                    }
                }.run();
                fail();
            } catch (Exception e) {
                assertThat(e.getMessage(), equalTo("requestAsync exception!"));
            }
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testTimedAsyncCallbackNoTimeout() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            final String res = callService(asyncService, 50);
            assertThat(res, equalTo("async result!"));
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testTimedAsyncCallbackWithTimeout() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(asyncService, 10);
                fail();
            } catch (TimeoutException ignored) {}
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testInterrupt1() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(longAsyncService);
                fail();
            } catch (InterruptedException ignored) {}
            return null;
        }).start();

        fiber.interrupt();
        fiber.join();
    }

    @Test
    public void testInterrupt2() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                callService(longAsyncService);
                fail();
            } catch (InterruptedException ignored) {}
            return null;
        }).start();

        Thread.sleep(100);
        fiber.interrupt();
        fiber.join();
    }

    @Test @Ignore // TODO an interrupted fiber waiting on a thread won't stop waiting in current Loom
    public void whenCancelRunBlockingInterruptExecutingThread() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean interrupted = new AtomicBoolean();

        final Fiber fiber = new Fiber<>(scheduler, () -> {
            FiberAsync.runBlocking(Executors.newSingleThreadExecutor(),
            (CheckedCallable<Void, RuntimeException>) () -> {
                started.set(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
                return null;
            });
            return null;
        });

        fiber.start();
        Thread.sleep(100);
        fiber.cancel(true);
        try {
            fiber.join(5, TimeUnit.MILLISECONDS);
            fail("InterruptedException not thrown");
        } catch(CompletionException e) {
            if (!(e.getCause() instanceof InterruptedException))
                fail("InterruptedException not thrown");
        }
        Thread.sleep(100);
        assertThat(started.get(), is(true));
        assertThat(interrupted.get(), is(true));
    }

    @Test
    public void testRunBlocking() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            final String res = FiberAsync.runBlocking(Executors.newCachedThreadPool(), () -> {
                Thread.sleep(300);
                return "ok";
            });
            assertThat(res, equalTo("ok"));
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testRunBlockingWithTimeout1() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            final String res = FiberAsync.runBlocking(Executors.newCachedThreadPool(), 400, TimeUnit.MILLISECONDS, () -> {
                Thread.sleep(300);
                return "ok";
            });
            assertThat(res, equalTo("ok"));
            return null;
        }).start();

        fiber.join();
    }

    @Test
    public void testRunBlockingWithTimeout2() throws Exception {
        final Fiber fiber = new Fiber<>(scheduler, () -> {
            try {
                FiberAsync.runBlocking(Executors.newCachedThreadPool(), 100, TimeUnit.MILLISECONDS, () -> {
                    Thread.sleep(300);
                    return "ok";
                });
                fail();
            } catch (TimeoutException ignored) {}
            return null;
        }).start();

        fiber.join();
    }
}
