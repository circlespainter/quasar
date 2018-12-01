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
import co.paralleluniverse.common.util.Debug;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import static co.paralleluniverse.strands.channels.Selector.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.hamcrest.CoreMatchers.*;
import org.junit.After;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

/**
 *
 * @author pron
 */
public class TransferSelectorTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private final int mailboxSize;
    private final OverflowPolicy policy;
    private final boolean singleConsumer;
    private final boolean singleProducer;
    private final ExecutorService scheduler;

    public TransferSelectorTest() {
        scheduler = Executors.newWorkStealingPool();
        this.mailboxSize = 0;
        this.policy = OverflowPolicy.BLOCK;
        this.singleConsumer = false;
        this.singleProducer = false;

        Debug.dumpAfter(20000, "channels.log");
    }

    private <Message> Channel<Message> newChannel() {
        return Channels.newChannel(mailboxSize, policy, singleProducer, singleConsumer);
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Test
    public void testSelectReceive1() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));

                final String m1 = sa1.message();

                final SelectAction<String> sa2 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));

                final String m2 = sa2.message();

                assertThat(sa1.index(), is(0));
                assertThat(m1, equalTo("hello"));
                assertThat(sa2.index(), is(2));
                assertThat(m2, equalTo("world!"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        channel1.send("hello");
        Thread.sleep(200);

        channel3.send("world!");

        fib.join();
    }

    @Test
    public void testSelectReceive2() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(200);
                SelectAction<String> sa1 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));


                co.paralleluniverse.strands.Strand.sleep(200);
                SelectAction<String> sa2 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));

                if (sa2.index() < sa1.index()) {
                    final SelectAction<String> tmp = sa1;
                    sa1 = sa2;
                    sa2 = tmp;
                }

                String m1 = sa1.message();
                String m2 = sa2.message();

                assertThat(sa1.index(), is(0));
                assertThat(m1, equalTo("hello"));
                assertThat(sa2.index(), is(2));
                assertThat(m2, equalTo("world!"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        channel1.send("hello");
        channel3.send("world!");

        fib.join();
    }

    @Test
    public void testSelectReceiveWithClose1() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));

                String m1 = sa1.message();

                final SelectAction<String> sa2 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));
                String m2 = sa2.message();

                assertThat(sa1.index(), is(2));
                assertThat(m1, nullValue());
                assertThat(m2, nullValue());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);
        channel3.close();

        fib.join();
    }

    @Test
    public void testSelectReceiveWithClose2() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa1 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));
                final String m1 = sa1.message();

                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa2 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));
                final String m2 = sa2.message();

                assertThat(sa1.index(), is(2));
                assertThat(m1, nullValue());
                assertThat(m2, nullValue());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        channel3.close();

        fib.join();
    }

    @Test
    public void testSelectReceiveTimeout() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(1, TimeUnit.MILLISECONDS,
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));

                final SelectAction<String> sa2 = select(300, TimeUnit.MILLISECONDS,
                        receive(channel1),
                        receive(channel2),
                        receive(channel3));
                final String m2 = sa2.message();

                assertThat(sa1, is(nullValue()));
                assertThat(sa2.index(), is(0));
                assertThat(m2, equalTo("hello"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        channel1.send("hello");

        fib.join();
    }

    @Test
    public void testSelectReceiveWithTimeoutChannel() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3),
                        receive(TimeoutChannel.<String>timeout(1, TimeUnit.MILLISECONDS)));

                final SelectAction<String> sa2 = select(
                        receive(channel1),
                        receive(channel2),
                        receive(channel3),
                        receive(TimeoutChannel.<String>timeout(300, TimeUnit.MILLISECONDS)));
                final String m2 = sa2.message();

                assertThat(sa1.index(), is(3));
                assertThat(sa2.index(), is(0));
                assertThat(m2, equalTo("hello"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }

        }).start();

        Thread.sleep(200);

        channel1.send("hello");

        fib.join();
    }

    @Test
    public void testSelectSend1() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                final SelectAction<String> sa2 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                assertThat(sa1.index(), is(1));
                assertThat(sa2.index(), is(0));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        final String m1 = channel2.receive();
        assertThat(m1, equalTo("hi2"));

        Thread.sleep(200);
        final String m2 = channel1.receive();
        assertThat(m2, equalTo("hi1"));

        fib.join();
    }

    @Test
    public void testSelectSend2() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa1 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa2 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                assertThat(sa1.index(), is(1));
                assertThat(sa2.index(), is(0));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final String m1 = channel2.receive();
        assertThat(m1, equalTo("hi2"));

        final String m2 = channel1.receive();
        assertThat(m2, equalTo("hi1"));

        fib.join();
    }

    @Test
    public void testSelectSendWithClose1() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                final SelectAction<String> sa2 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                assertThat(sa1.index(), is(1));
                assertThat(sa2.index(), is(1));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        channel2.close();

        fib.join();
    }

    @Test
    public void testSelectSendWithClose2() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa1 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                co.paralleluniverse.strands.Strand.sleep(200);
                final SelectAction<String> sa2 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                assertThat(sa1.index(), is(1));
                assertThat(sa2.index(), is(1));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        channel2.close();

        fib.join();
    }

    @Test
    public void testSelectSendTimeout() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(1, TimeUnit.MILLISECONDS,
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"));

                final SelectAction<String> sa2 = select(300, TimeUnit.MILLISECONDS,
                        send(channel1, "bye1"),
                        send(channel2, "bye2"),
                        send(channel3, "bye3"));

                assertThat(sa1, is(nullValue()));
                assertThat(sa2.index(), is(2));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        String m1 = channel3.receive();

        assertThat(m1, equalTo("bye3")); // the first send is cancelled

        fib.join();
    }

    @Test
    public void testSelectSendWithTimeoutChannel() throws Exception {
        final Channel<String> channel1 = newChannel();
        final Channel<String> channel2 = newChannel();
        final Channel<String> channel3 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SelectAction<String> sa1 = select(
                        send(channel1, "hi1"),
                        send(channel2, "hi2"),
                        send(channel3, "hi3"),
                        receive(TimeoutChannel.<String>timeout(1, TimeUnit.MILLISECONDS)));

                final SelectAction<String> sa2 = select(
                        send(channel1, "bye1"),
                        send(channel2, "bye2"),
                        send(channel3, "bye3"),
                        receive(TimeoutChannel.<String>timeout(300, TimeUnit.MILLISECONDS)));

                assertThat(sa1.index(), is(3));
                assertThat(sa2.index(), is(2));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        Thread.sleep(200);

        final String m1 = channel3.receive();

        assertThat(m1, equalTo("bye3")); // the first send is cancelled

        fib.join();
    }
}
