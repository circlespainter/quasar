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
import co.paralleluniverse.common.util.Exceptions;
import co.paralleluniverse.strands.Condition;
import co.paralleluniverse.strands.SimpleConditionSynchronizer;
import co.paralleluniverse.strands.Strand;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 *
 * @author pron
 */

@RunWith(Parameterized.class)
public class FiberTest implements Serializable {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private transient ExecutorService scheduler;

    public FiberTest(ExecutorService scheduler) {
        this.scheduler = scheduler;
    }

//    @After
//    public void tearDown() {
          // Cannot shutdown FiberScheduler, as it is reused over multiple tests. So these FiberSchedulers and their associated threads will be kept over the whole test run.
//        scheduler.shutdown();
//    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {Executors.newWorkStealingPool()},
            {Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("fiber-scheduler-%d").setDaemon(true).build())}});
    }

    private static co.paralleluniverse.strands.Strand.UncaughtExceptionHandler previousUEH;

    @BeforeClass
    public static void setupClass() {
        previousUEH = Fiber.getDefaultUncaughtExceptionHandler();
        Fiber.setDefaultUncaughtExceptionHandler((s, e) -> Exceptions.rethrow(e));
    }

    @AfterClass
    public static void afterClass() {
        // Restore
        Fiber.setDefaultUncaughtExceptionHandler(previousUEH);
    }

    @Test
    public void testTimeout() throws Exception {
        Fiber fiber = new Fiber(scheduler, () -> Fiber.park(100, TimeUnit.MILLISECONDS)).start();

        try {
            fiber.join(50, TimeUnit.MILLISECONDS);
            fail();
        } catch (final TimeoutException ignored) {}

        fiber.join(200, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testJoinFromFiber() throws Exception {
        final Fiber<Integer> fiber1 = new Fiber<>(scheduler, () -> {
            Fiber.park(100, TimeUnit.MILLISECONDS);
            return 123;
        }).start();

        final Fiber<Integer> fiber2 = new Fiber<>(scheduler, (Callable<Integer>) fiber1::get).start();

        int res = fiber2.get();

        assertThat(res, is(123));
        assertThat(fiber1.get(), is(123));
    }

    @Test
    public void testInterrupt() throws Exception {
        final Fiber fiber = new Fiber(scheduler, () -> {
            try {
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException ignored) {}
        }).start();

        Thread.sleep(20);
        fiber.interrupt();
        fiber.join(5, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCancelSleepingFiberInterruptible() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean terminated = new AtomicBoolean();
        final Fiber fiber = new Fiber(scheduler, () -> {
            started.set(true);
            try {
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException ignored) {}
            terminated.set(true);
        });

        fiber.start();
        Thread.sleep(20);
        fiber.cancel(true);
        fiber.join(5, TimeUnit.MILLISECONDS);
        assertThat(started.get(), is(true));
        assertThat(terminated.get(), is(true));
    }

    @Test
    public void testCancelFiberBeforeStart() throws Exception {
        final AtomicBoolean started = new AtomicBoolean();
        final AtomicBoolean terminated = new AtomicBoolean();
        final Fiber fiber = new Fiber(scheduler, () -> {
            started.set(true);
            try {
                Fiber.sleep(100);
                fail("InterruptedException not thrown");
            } catch (InterruptedException ignored) {}
            terminated.set(true);
        });

        fiber.cancel(true);
        fiber.start();
        Thread.sleep(20);
        try {
            fiber.join(5, TimeUnit.MILLISECONDS);
            fail();
        } catch (final CancellationException ignored) {}
        assertThat(started.get(), is(false));
        assertThat(terminated.get(), is(false));
    }

    @Test
    public void testThreadLocals() throws Exception {
        final ThreadLocal<String> tl1 = new ThreadLocal<>();
        final InheritableThreadLocal<String> tl2 = new InheritableThreadLocal<>();
        tl1.set("foo");
        tl2.set("bar");

        final Fiber<Void> fiber = new Fiber<>(scheduler, () -> {
            assertThat(tl1.get(), is(nullValue()));
            assertThat(tl2.get(), is("bar"));

            tl1.set("koko");
            tl2.set("bubu");

            assertThat(tl1.get(), is("koko"));
            assertThat(tl2.get(), is("bubu"));

            Fiber.sleep(100);

            assertThat(tl1.get(), is("koko"));
            assertThat(tl2.get(), is("bubu"));

            return null;
        });
        fiber.start();
        fiber.join();

        assertThat(tl1.get(), is("foo"));
        assertThat(tl2.get(), is("bar"));
    }

    @Test
    public void testInheritThreadLocals() throws Exception {
        final InheritableThreadLocal<String> tl1 = new InheritableThreadLocal<>();
        tl1.set("foo");
        assertThat(tl1.get(), is("foo"));

        final Fiber<Void> fiber = new Fiber<>(scheduler, () -> {
            assertThat(tl1.get(), is("foo"));

            Fiber.sleep(100);

            assertThat(tl1.get(), is("foo"));

            tl1.set("koko");

            assertThat(tl1.get(), is("koko"));

            Fiber.sleep(100);

            assertThat(tl1.get(), is("koko"));

            return null;
        });
        fiber.start();
        fiber.join();

        assertThat(tl1.get(), is("foo"));
    }

    @Test
    public void testThreadLocalsParallel() throws Exception {
        final ThreadLocal<String> tl = new ThreadLocal<>();

        final int n = 100;
        final int loops = 100;
        final Fiber[] fibers = new Fiber[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            final Fiber<Void> fiber = new Fiber<>(scheduler, () -> {
                for (int j = 0; j < loops; j++) {
                    final String tlValue = "tl-" + id + "-" + j;
                    tl.set(tlValue);
                    assertThat(tl.get(), equalTo(tlValue));
                    Strand.sleep(10);
                    assertThat(tl.get(), equalTo(tlValue));
                }

                return null;
            });
            fiber.start();
            fibers[i] = fiber;
        }

        for (final Fiber fiber : fibers)
            fiber.join();
    }

    @Test
    public void testInheritThreadLocalsParallel() throws Exception {
        final ThreadLocal<String> tl = new ThreadLocal<>();
        tl.set("foo");

        final int n = 100;
        final int loops = 100;
        Fiber[] fibers = new Fiber[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            final Fiber<Void> fiber = new Fiber<>(scheduler, () -> {
                for (int j = 0; j < loops; j++) {
                    final String tlValue = "tl-" + id + "-" + j;
                    tl.set(tlValue);
                    assertThat(tl.get(), equalTo(tlValue));
                    Strand.sleep(10);
                    assertThat(tl.get(), equalTo(tlValue));
                }
                return null;
            });
            fiber.start();
            fibers[i] = fiber;
        }

        for (Fiber fiber : fibers)
            fiber.join();
    }

    @Test
    public void whenFiberIsNewThenDumpStackReturnsNull() throws Exception {
        final Fiber fiber = new Fiber(scheduler, new Runnable() {
            @Override
            public void run() {
                foo();
            }

            private void foo() {
            }
        });

        final StackTraceElement[] st = fiber.getStackTrace();
        assertThat(st, is(nullValue()));
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void whenFiberIsTerminatedThenDumpStackReturnsNull() throws Exception {
        final Fiber fiber = new Fiber(scheduler, new Runnable() {
            @Override
            public void run() {
                foo();
            }

            private void foo() {
            }
        }).start();

        fiber.join();

        final StackTraceElement[] st = fiber.getStackTrace();
        assertThat(st, is(nullValue()));
    }

    @Test
    public void testDumpStackCurrentFiber() {
        final Fiber fiber = new Fiber(scheduler, new Runnable() {
            @Override
            public void run() {
                foo();
            }

            private void foo() {
                final StackTraceElement[] st = Fiber.currentFiber().getStackTrace();

                // Strand.printStackTrace(st, System.err);
                assertTrue(st.length > 1);
                assertThat(st[0].getMethodName(), equalTo("getStackTrace"));
            }
        }).start();

        fiber.join();
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void testDumpStackRunningFiber() throws Exception {
        final Fiber fiber = new Fiber(scheduler, new Runnable() {
            @Override
            public void run() {
                foo();
            }

            private void foo() {
                final long start = System.nanoTime();
                for (;;) {
                    if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > 1000)
                        break;
                }
            }
        }).start();

        Thread.sleep(200);

        final StackTraceElement[] st = fiber.getStackTrace();

        // Strand.printStackTrace(st, System.err);
        boolean found = false;
        for (final StackTraceElement aSt : st) {
            if (aSt.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found, is(true));
        assertThat(st[st.length - 1].getMethodName(), equalTo("run"));
        assertThat(st[st.length - 1].getClassName(), equalTo(Fiber.class.getName()));

        fiber.join();
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void testDumpStackWaitingFiber() throws Exception {
        final Condition cond = new SimpleConditionSynchronizer(null);
        final AtomicBoolean flag = new AtomicBoolean(false);

        final Fiber<Void> fiber = new Fiber<>(scheduler, new Callable<Void>() {
            @Override
            public Void call() throws InterruptedException {
                foo();
                return null;
            }

            private void foo() throws InterruptedException {
                final Object token = cond.register();
                try {
                    for (int i = 0; !flag.get(); i++)
                        cond.await(i);
                } finally {
                    cond.unregister(token);
                }
            }
        }).start();

        Thread.sleep(200);

        final StackTraceElement[] st = fiber.getStackTrace();

        assertNotNull(st);
        assertTrue(st.length > 0);
        assertThat(st[0].getMethodName(), equalTo("park"));
        boolean found = false;
        for (final StackTraceElement ste : st) {
            if (ste.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found, is(true));
        assertThat(st[st.length - 1].getMethodName(), equalTo("run"));
        assertThat(st[st.length - 1].getClassName(), equalTo(Fiber.class.getName()));

        flag.set(true);
        cond.signalAll();

        fiber.join();
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void testDumpStackWaitingFiberWhenCalledFromFiber() throws Exception {
        final Condition cond = new SimpleConditionSynchronizer(null);
        final AtomicBoolean flag = new AtomicBoolean(false);

        final Fiber<Void> fiber = new Fiber<>(scheduler, new Callable<Void>() {
            @Override
            public Void call() throws InterruptedException {
                foo();
                return null;
            }

            private void foo() throws InterruptedException {
                final Object token = cond.register();
                try {
                    for (int i = 0; !flag.get(); i++)
                        cond.await(i);
                } finally {
                    cond.unregister(token);
                }
            }
        }).start();

        Thread.sleep(200);

        final Fiber fiber2 = new Fiber(scheduler, () -> {
            final StackTraceElement[] st = fiber.getStackTrace();

            // Strand.printStackTrace(st, System.err);
            assertThat(st[0].getMethodName(), equalTo("park"));
            boolean found = false;
            for (final StackTraceElement ste : st) {
                if (ste.getMethodName().equals("foo")) {
                    found = true;
                    break;
                }
            }
            assertThat(found, is(true));
            assertThat(st[st.length - 1].getMethodName(), equalTo("run"));
            assertThat(st[st.length - 1].getClassName(), equalTo(Fiber.class.getName()));
        }).start();

        fiber2.join();

        flag.set(true);
        cond.signalAll();

        fiber.join();
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void testDumpStackWaitingFiberWhenCalledFromFiber_JDKLoom() throws Exception {
        final AtomicBoolean flag = new AtomicBoolean(false);

        final AtomicReference<java.lang.Thread> fiberThreadRef = new AtomicReference<>();
        final java.lang.Fiber fiber = java.lang.Fiber.schedule(scheduler, new Runnable() {
            @Override
            public void run() {
                fiberThreadRef.set(Thread.currentThread());
                try {
                    foo();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            private void foo() throws InterruptedException {
                while (!flag.get())
                    Thread.sleep(10);
            }
        });

        Thread.sleep(100);

        final CompletableFuture<Void> res = new CompletableFuture<>();
        java.lang.Fiber.schedule(scheduler, () -> {
            try {
                assertNotNull(fiber);
                assertNotNull(fiberThreadRef.get());
                assertTrue(fiber.isAlive());
                assertTrue(fiberThreadRef.get().isAlive());

                final StackTraceElement[] st = fiberThreadRef.get().getStackTrace();
                assertNotNull(st);
                assertTrue(st.length > 0);

                // Strand.printStackTrace(st, System.err);
                assertThat(st[0].getMethodName(), equalTo("park"));
                boolean found = false;
                for (final StackTraceElement ste : st) {
                    if (ste.getMethodName().equals("foo")) {
                        found = true;
                        break;
                    }
                }
                assertThat(found, is(true));
                assertThat(st[st.length - 1].getMethodName(), equalTo("run"));
                assertThat(st[st.length - 1].getClassName(), equalTo(Fiber.class.getName()));
                res.complete(null);
            } catch (final Throwable t) {
                t.printStackTrace();
                res.completeExceptionally(t);
            }
        });

        flag.set(true);
        try {
            assertNull(res.get());
        } catch (final Exception e) {
            fail();
        }
    }

    @Test @Ignore // TODO Getting a fiber's trace from other strands is currently unimplemented in Loom
    public void testDumpStackSleepingFiber() throws Exception {
        // sleep is a special case
        final Fiber<Void> fiber = new Fiber<>(scheduler, new Callable<Void>() {
            @Override
            public Void call() throws InterruptedException {
                foo();
                return null;
            }

            private void foo() throws InterruptedException {
                Fiber.sleep(1000);
            }
        }).start();

        Thread.sleep(200);

        final StackTraceElement[] st = fiber.getStackTrace();

        assertNotNull(st);
        assertTrue(st.length > 0);
        assertThat(st[0].getMethodName(), equalTo("sleep"));
        boolean found = false;
        for (final StackTraceElement aSt : st) {
            if (aSt.getMethodName().equals("foo")) {
                found = true;
                break;
            }
        }
        assertThat(found, is(true));
        assertThat(st[st.length - 1].getMethodName(), equalTo("run"));
        assertThat(st[st.length - 1].getClassName(), equalTo(Fiber.class.getName()));

        fiber.join();
    }

    @Test
    public void testBadFiberDetection() throws Exception {
        final Fiber<Void> good = new Fiber<>(scheduler, (Callable<Void>) () -> {
            for (int i = 0; i < 100; i++)
                Strand.sleep(10);
            return null;
        }).start();

        final Fiber<Void> bad = new Fiber<>("bad", scheduler, (Callable<Void>) () -> {
            final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000);
            for (;;) {
                if (System.nanoTime() >= deadline)
                    break;
            }
            return null;
        }).start();

        good.join();
        bad.join();
    }

    @Test
    public void testUncaughtExceptionHandler() throws Exception {
        final AtomicReference<Throwable> t = new AtomicReference<>();

        final Fiber<Void> f = new Fiber<>(() -> { throw new RuntimeException("foo"); });
        f.setUncaughtExceptionHandler((f1, e) -> t.set(e));

        f.start();

        try {
            f.join();
            fail();
        } catch (CompletionException e) {
            assertThat(e.getCause().getMessage(), equalTo("foo"));
        }

        assertThat(t.get().getMessage(), equalTo("foo"));
    }

    @Test
    public void testDefaultUncaughtExceptionHandler() throws Exception {
        final AtomicReference<Throwable> t = new AtomicReference<>();

        final Fiber<Void> f = new Fiber<>(() -> { throw new RuntimeException("foo"); });
        Fiber.setDefaultUncaughtExceptionHandler((f1, e) -> t.set(e));

        f.start();

        try {
            f.join();
            fail();
        } catch (CompletionException e) {
            assertThat(e.getCause().getMessage(), equalTo("foo"));
        }
        final Throwable th = t.get();

        assertNotNull(th);
        assertThat(th.getMessage(), equalTo("foo"));
    }

    @Test
    public void testUtilsGet() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> "testUtilsSequence-" + tmpI).start());
        }

        final List<String> results = FiberUtil.get(fibers);
        assertThat(results, equalTo(expectedResults));
    }

    @Test
    public void testUtilsGetWithTimeout() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> "testUtilsSequence-" + tmpI).start());
        }

        final List<String> results = FiberUtil.get(1, TimeUnit.SECONDS, fibers);
        assertThat(results, equalTo(expectedResults));
    }

    @Test(expected = TimeoutException.class)
    public void testUtilsGetZeroWait() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(new Callable<String>() {
                @Override
                public String call() {
                    return "testUtilsSequence-" + tmpI;
                }
            }).start());
        }

        final List<String> results = FiberUtil.get(0, TimeUnit.SECONDS, fibers);
        assertThat(results, equalTo(expectedResults));
    }

    @Test(expected = TimeoutException.class)
    public void testUtilsGetSmallWait() throws Exception {
        final List<Fiber<String>> fibers = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int tmpI = i;
            expectedResults.add("testUtilsSequence-" + tmpI);
            fibers.add(new Fiber<>(() -> {
                // increase the sleep time to simulate data coming in then timeout
                Strand.sleep(tmpI * 3, TimeUnit.MILLISECONDS);
                return "testUtilsSequence-" + tmpI;
            }).start());
        }

        // must be less than 60 (3 * 20) or else the test could sometimes pass.
        final List<String> results = FiberUtil.get(55, TimeUnit.MILLISECONDS, fibers);
        assertThat(results, equalTo(expectedResults));
    }

}
