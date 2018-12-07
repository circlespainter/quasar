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
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

/**
 *
 * @author pron
 */
public class VarTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    public VarTest() {
    }

    @Test
    public void testThreadWaiter() throws Exception {
        final Var<String> var = new Var<>();

        final AtomicReference<String> res = new AtomicReference<>();

        final Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                final String v = var.get();
                res.set(v);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));
        t1.start();

        Thread.sleep(100);

        var.set("yes!");

        t1.join();

        assertThat(res.get(), equalTo("yes!"));
        assertThat(var.get(), equalTo("yes!"));
    }

    @Test
    public void testFiberWaiter() throws Exception {
        final Var<String> var = new Var<>();

        final co.paralleluniverse.fibers.Fiber<String> f1 = new co.paralleluniverse.fibers.Fiber<>(var::get).start();

        Thread.sleep(100);

        var.set("yes!");

        f1.join();

        assertThat(f1.get(), equalTo("yes!"));
        assertThat(var.get(), equalTo("yes!"));
    }

    @Test
    public void testThreadAndFiberWaiters() throws Exception {
        final Var<String> var = new Var<>();

        final AtomicReference<String> res = new AtomicReference<>();

        final co.paralleluniverse.fibers.Fiber<String> f1 = new co.paralleluniverse.fibers.Fiber<>(var::get).start();

        final Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(() -> {
            try {
                final String v = var.get();
                res.set(v);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }));
        t1.start();

        Thread.sleep(100);

        var.set("yes!");

        t1.join();
        f1.join();

        assertThat(f1.get(), equalTo("yes!"));
        assertThat(res.get(), equalTo("yes!"));
        assertThat(var.get(), equalTo("yes!"));
    }

    @Test
    public void testHistory1() throws Exception {
        final Var<Integer> var = new Var<>(10);

        final co.paralleluniverse.fibers.Fiber<Integer> f1 = new co.paralleluniverse.fibers.Fiber<>(() -> {
            co.paralleluniverse.strands.Strand.sleep(100);
            int sum = 0;
            for (int i = 0; i < 10; i++)
                sum += var.get();
            return sum;
        }).start();

        final co.paralleluniverse.fibers.Fiber<Integer> f2 = new co.paralleluniverse.fibers.Fiber<>(() -> {
            co.paralleluniverse.strands.Strand.sleep(100);
            int sum = 0;
            for (int i = 0; i < 10; i++)
                sum += var.get();
            return sum;
        }).start();

        for (int i = 0; i < 10; i++)
            var.set(i + 1);

        assertThat(f1.get(), equalTo(55));
        assertThat(f2.get(), equalTo(55));
    }

    @Test
    public void testHistory2() throws Exception {
        final Var<Integer> var = new Var<>(2);

        final co.paralleluniverse.fibers.Fiber<Integer> f1 = new co.paralleluniverse.fibers.Fiber<>(() -> {
            co.paralleluniverse.strands.Strand.sleep(100);
            int sum = 0;
            for (int i = 0; i < 10; i++)
                sum += var.get();
            return sum;
        }).start();

        final co.paralleluniverse.fibers.Fiber<Integer> f2 = new co.paralleluniverse.fibers.Fiber<>(() -> {
            int sum = 0;
            for (int i = 0; i < 10; i++)
                sum += var.get();
            return sum;
        }).start();

        for (int i = 0; i < 10; i++)
            var.set(i + 1);

        assertThat(f1.get(), not(equalTo(55)));
        f2.join();
    }

    @Test @Ignore
    public void testFunction1() throws Exception {
        final Channel<Integer> ch = Channels.newChannel(-1);

        final Var<Integer> a = new Var<>();
        final Var<Integer> b = new Var<>();

        final Var<Integer> var = new Var<>(2, () -> {
            System.out.println("called");
            int c = a.get() + b.get();
            System.out.println("sending");
            ch.send(c);
            System.out.println("sent " + c);
            return c;
        });

        b.set(2);
        assertThat(ch.receive(50, MILLISECONDS), is(nullValue()));

        a.set(1);
        assertThat(ch.receive(50, MILLISECONDS), is(3));

        assertThat(ch.receive(), is(3));

        assertThat(ch.receive(50, MILLISECONDS), is(nullValue()));

        a.set(2);
        assertThat(ch.receive(), is(4));

        assertThat(ch.receive(50, MILLISECONDS), is(nullValue()));
        b.set(3);
        assertThat(ch.receive(50, MILLISECONDS), is(5));

        assertThat(ch.receive(50, MILLISECONDS), is(nullValue()));
        a.set(4);
        assertThat(ch.receive(50, MILLISECONDS), is(7));
    }
}
