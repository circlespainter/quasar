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

import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import static co.paralleluniverse.strands.channels.Selector.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.*;
import org.junit.After;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author pron
 */
@RunWith(Parameterized.class)
public class GeneralSelectorTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private final boolean fiber;
    private final int mailboxSize;
    private final OverflowPolicy policy;
    private final boolean singleConsumer;
    private final boolean singleProducer;
    private final ExecutorService scheduler;

//    public GeneralSelectorTest() {
//        fjPool = new ForkJoinPool(4, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
//        this.mailboxSize = 1;
//        this.policy = OverflowPolicy.BLOCK;
//        this.singleConsumer = false;
//        this.singleProducer = false;
//        this.fiber = false;
//
//        Debug.dumpAfter(15000, "channels.log");
//    }
    public GeneralSelectorTest(int mailboxSize, OverflowPolicy policy, boolean singleConsumer, boolean singleProducer) {
        scheduler = Executors.newWorkStealingPool();
        this.mailboxSize = mailboxSize;
        this.policy = policy;
        this.singleConsumer = singleConsumer;
        this.singleProducer = singleProducer;
        fiber = true;
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                    {5, OverflowPolicy.BLOCK, false, false},
                    {1, OverflowPolicy.BLOCK, false, false},
                    {-1, OverflowPolicy.THROW, true, false},
                    {5, OverflowPolicy.DISPLACE, true, false},
                    {5, OverflowPolicy.DROP, true, false},
                    {0, OverflowPolicy.BLOCK, false, false},});
    }

    private <Message> Channel<Message> newChannel() {
        return Channels.newChannel(mailboxSize, policy, singleProducer, singleConsumer);
    }

    private void spawn(Runnable r) {
        if (fiber)
            new co.paralleluniverse.fibers.Fiber(scheduler, r).start();
        else
            new Thread(co.paralleluniverse.strands.Strand.toRunnable(r)).start();
    }

    private <Message> Channel<Message>[] fanOut(final ReceivePort<Message> in, final int n) {
        @SuppressWarnings("unchecked") final Channel<Message>[] chans = new Channel[n];
        for (int i = 0; i < n; i++)
            chans[i] = newChannel();
        spawn(() -> {
            for (;;) {
                Message m;
                try {
                    m = in.receive();
                    // System.out.println("fanout: " + m);
                    if (m == null) {
                        for (final Channel<Message> c : chans)
                            c.close();
                        break;
                    } else {
                        final List<SelectAction<Message>> as = new ArrayList<>(n);
                        for (final Channel<Message> c : chans)
                            as.add(send(c, m));
                        SelectAction<Message> sa = select(as);
                        // System.out.println("Wrote to " + sa.index());
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
            //System.err.println("fanout done");
        });
        return chans;
    }

    private <Message> Channel<Message> fanIn(final ReceivePort<Message>[] ins) {
        final Channel<Message> chan = newChannel();

        spawn(() -> {
            for (;;) {
                try {
                    final List<SelectAction<Message>> as = new ArrayList<>(ins.length);
                    for (final ReceivePort<Message> c : ins)
                        as.add(receive(c));
                    final SelectAction<Message> sa = select(as);
                    // System.out.println("Received from " + sa.index());

                    final Message m = sa.message();
                    // System.out.println("fanin: " + m);
                    if (m == null) {
                        chan.close();
                        break;
                    } else {
                        chan.send(m);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }

            }
            //System.err.println("fanin done");
        });
        return chan;
    }

    @Test
    public void testFans1() throws Exception {
        final int nchans = 3;
        final int n = 200;

        final Channel<Integer> out = newChannel();
        final Channel<Integer> in = fanIn(fanOut(out, nchans));

        for (int i = 0; i < n; i++) {
            //System.out.println("send: " + i);
            out.send(i);
            //System.out.println("receiving");
            final Integer x = in.receive();
            //System.out.println("received " + x);
            assertThat(x, is(i));
        }
        out.close();
        assertThat(in.receive(), nullValue());
        assertThat(in.isClosed(), is(true));
    }

    @Test
    public void testFans2() throws Exception {
        assumeThat(mailboxSize, is(1));
        final int nchans = 10;

        final Channel<Integer> out = newChannel();
        final Channel<Integer> in = fanIn(fanOut(out, nchans));

        for (int i = 0; i < nchans; i++) {
            out.send(i);
        }

        Thread.sleep(500);

        boolean[] ms = new boolean[nchans];
        for (int i = 0; i < nchans; i++) {
            final Integer m = in.receive();
            ms[m] = true;
        }
        for (int i = 0; i < nchans; i++)
            assertThat(ms[i], is(true));


        out.close();
        assertThat(in.receive(), nullValue());
        assertThat(in.isClosed(), is(true));
    }
}
