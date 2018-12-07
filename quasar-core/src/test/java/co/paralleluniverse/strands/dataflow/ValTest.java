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
package co.paralleluniverse.strands.dataflow;

import co.paralleluniverse.common.test.TestUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

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
public class ValTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    public ValTest() {
    }

    @Test(expected = IllegalStateException.class)
    public void whenValIsSetTwiceThenThrowException() {
        final Val<String> val = new Val<>();

        val.set("hello");
        val.set("goodbye");
    }

    @Test
    public void testThreadWaiter() throws Exception {
        final Val<String> val = new Val<>();

        final AtomicReference<String> res = new AtomicReference<>();

        final Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                final String v = val.get();
                res.set(v);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));
        t1.start();

        Thread.sleep(100);

        val.set("yes!");

        t1.join();

        assertThat(res.get(), equalTo("yes!"));
        assertThat(val.get(), equalTo("yes!"));
    }

    @Test
    public void testFiberWaiter() throws Exception {
        final Val<String> val = new Val<>();

        final co.paralleluniverse.fibers.Fiber<String> f1 =
                new co.paralleluniverse.fibers.Fiber<>((Callable<String>) val::get).start();

        Thread.sleep(100);

        val.set("yes!");

        f1.join();

        assertThat(f1.get(), equalTo("yes!"));
        assertThat(val.get(), equalTo("yes!"));
    }

    @Test
    public void testThreadAndFiberWaiters() throws Exception {
        final Val<String> val = new Val<>();

        final AtomicReference<String> res = new AtomicReference<>();

        final co.paralleluniverse.fibers.Fiber<String> f1 =
                new co.paralleluniverse.fibers.Fiber<>((Callable<String>) val::get).start();

        final Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                final String v = val.get();
                res.set(v);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));
        t1.start();

        Thread.sleep(100);

        val.set("yes!");

        t1.join();
        f1.join();

        assertThat(f1.get(), equalTo("yes!"));
        assertThat(res.get(), equalTo("yes!"));
        assertThat(val.get(), equalTo("yes!"));
    }

    @Test
    public void complexTest1() throws Exception {
        final Val<Integer> val1 = new Val<>();
        final Val<Integer> val2 = new Val<>();

        final AtomicReference<Integer> res = new AtomicReference<>();

        final co.paralleluniverse.fibers.Fiber<Integer> f1 = new co.paralleluniverse.fibers.Fiber<>(() ->
            val1.get() + val2.get()
        ).start();

        final Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                res.set(val1.get() * val2.get());
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));
        t1.start();

        final co.paralleluniverse.fibers.Fiber<Integer> f2 = new co.paralleluniverse.fibers.Fiber<Integer>(() -> val2.set(5)).start();

        Thread.sleep(100);

        val1.set(8);

        final int myRes = val1.get() - val2.get();
        t1.join();
        f1.join();

        assertThat(f1.get(), equalTo(13));
        assertThat(res.get(), equalTo(40));
        assertThat(myRes, is(3));

        f2.join();
    }

    @Test
    public void complexTest2() throws Exception {
        final Val<Integer> val1 = new Val<>();
        final Val<Integer> val2 = new Val<>();
        final Val<Integer> val3 = new Val<>();
        final Val<Integer> val4 = new Val<>();

        final co.paralleluniverse.strands.Strand f1 = new co.paralleluniverse.fibers.Fiber<Integer>(() -> {
            try {
                val2.set(val1.get() + 1); // 2
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        final co.paralleluniverse.strands.Strand t1 = co.paralleluniverse.strands.Strand.of(new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                val3.set(val1.get() + val2.get()); // 3
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }))).start();

        final co.paralleluniverse.strands.Strand f2 = new co.paralleluniverse.fibers.Fiber<Integer>(() -> {
            try {
                val4.set(val2.get() + val3.get()); // 5
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(100);

        val1.set(1);

        final int myRes = val4.get();
        assertThat(myRes, is(5));

        t1.join();
        f1.join();
        f2.join();
    }
}
