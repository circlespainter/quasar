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
package co.paralleluniverse.strands.channels;

import static co.paralleluniverse.common.test.Matchers.*;
import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.common.util.Debug;

import static org.hamcrest.CoreMatchers.*;
import org.junit.After;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author pron
 */
public class TickerChannelTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private static final int bufferSize = 10;
    private ExecutorService scheduler;

    public TickerChannelTest() {
        scheduler = Executors.newWorkStealingPool();
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Test
    public void testMultipleConsumersAlwaysAscending() throws Exception {
        final Channel<Integer> sch = Channels.newChannel(bufferSize, Channels.OverflowPolicy.DISPLACE);

        final Runnable run = () -> {
            // System.out.println(Strand.currentStrand() + ": starting");
            final ReceivePort<Integer> ch = Channels.newTickerConsumerFor(sch);
            int prev = -1;
            long prevIndex = -1;
            Integer m;
            try {
                while ((m = ch.receive()) != null) {
                    //System.out.println(Strand.currentStrand() + ": " + m);
                    long index = ((TickerChannelConsumer) ch).getLastIndexRead();
                    assertThat("index", index, greaterThan(prevIndex));
                    assertThat("message", m, greaterThan(prev));

                    prev = m;
                    prevIndex = index;
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }

            assertThat(ch.isClosed(), is(true));
        };

        int i = 1;

        for (; i < 50; i++)
            sch.send(i);
        co.paralleluniverse.fibers.Fiber f1 = new co.paralleluniverse.fibers.Fiber(scheduler, run).start();
        Thread t1 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(run));
        t1.start();
        for (; i < 200; i++)
            sch.send(i);
        co.paralleluniverse.fibers.Fiber f2 = new co.paralleluniverse.fibers.Fiber(scheduler, run).start();
        Thread t2 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(run));
        t2.start();
        for (; i < 600; i++)
            sch.send(i);
        co.paralleluniverse.fibers.Fiber f3 = new co.paralleluniverse.fibers.Fiber(scheduler, run).start();
        Thread t3 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(run));
        t3.start();
        for (; i < 800; i++)
            sch.send(i);
        co.paralleluniverse.fibers.Fiber f4 = new co.paralleluniverse.fibers.Fiber(scheduler, run).start();
        Thread t4 = new Thread(co.paralleluniverse.strands.Strand.toRunnable(run));
        t4.start();
        for (; i < 2000; i++)
            sch.send(i);

        sch.close();
        System.out.println("done send");

        Debug.dumpAfter(5000, "ticker.log");
        f1.join();
        System.out.println("f1: " + f1);
        f2.join();
        System.out.println("f2: " + f2);
        f3.join();
        System.out.println("f3: " + f3);
        f4.join();
        System.out.println("f4: " + f4);
        t1.join();
        System.out.println("t1: " + t1);
        t2.join();
        System.out.println("t2: " + t2);
        t3.join();
        System.out.println("t3: " + t3);
        t4.join();
        System.out.println("t4: " + t4);
    }
}
