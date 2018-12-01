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
package co.paralleluniverse.strands.channels;

import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.common.util.Action2;
import co.paralleluniverse.common.util.Function2;
import co.paralleluniverse.strands.Timeout;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static co.paralleluniverse.common.test.Matchers.greaterThan;
import static co.paralleluniverse.common.test.Matchers.lessThan;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

/**
 *
 * @author pron
 */
@RunWith(Parameterized.class)
public class TransformingChannelTest {
    private static final Object GO = new Object();

    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private final int mailboxSize;
    private final OverflowPolicy policy;
    private final boolean singleConsumer;
    private final boolean singleProducer;
    private final ExecutorService scheduler;

//    public ChannelTest() {
//        fjPool = new ForkJoinPool(4, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
//        this.mailboxSize = 0;
//        this.policy = OverflowPolicy.BLOCK;
//        this.singleConsumer = false;
//        this.singleProducer = false;
//
//        Debug.dumpAfter(20000, "channels.log");
//    }
    public TransformingChannelTest(int mailboxSize, OverflowPolicy policy, boolean singleConsumer, boolean singleProducer) {
        scheduler = Executors.newWorkStealingPool();
        this.mailboxSize = mailboxSize;
        this.policy = policy;
        this.singleConsumer = singleConsumer;
        this.singleProducer = singleProducer;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {5, OverflowPolicy.THROW, true, false},
            {5, OverflowPolicy.THROW, false, false},
            {5, OverflowPolicy.BLOCK, true, false},
            {5, OverflowPolicy.BLOCK, false, false},
            {1, OverflowPolicy.BLOCK, false, false},
            {-1, OverflowPolicy.THROW, true, false},
            {5, OverflowPolicy.DISPLACE, true, false},
            {0, OverflowPolicy.BLOCK, false, false},});
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
    public void transformingReceiveChannelIsEqualToChannel() {
        final Channel<Integer> ch = newChannel();
        final ReceivePort<Integer> ch1 = Channels.filter(ch, input -> input % 2 == 0);
        final ReceivePort<Integer> ch2 = Channels.map(ch, input -> input + 10);
        final ReceivePort<Integer> ch3 = Channels.flatMap(ch, input -> Channels.toReceivePort(Arrays.asList(input * 10, input * 100, input * 1000)));
        final ReceivePort<Integer> ch4 = Channels.reduce(ch, (accum, input) -> (accum += input), 0);
        final ReceivePort<Integer> ch5 = Channels.take(ch, 1);

        assertEquals(ch1, ch);
        assertEquals(ch, ch1);
        assertEquals(ch2, ch);
        assertEquals(ch, ch2);
        assertEquals(ch3, ch);
        assertEquals(ch, ch3);
        assertEquals(ch4, ch);
        assertEquals(ch, ch4);
        assertEquals(ch5, ch);
        assertEquals(ch, ch5);

        assertEquals(ch1, ch1);
        assertEquals(ch1, ch2);
        assertEquals(ch1, ch3);
        assertEquals(ch1, ch4);
        assertEquals(ch1, ch5);
        assertEquals(ch2, ch1);
        assertEquals(ch2, ch2);
        assertEquals(ch2, ch3);
        assertEquals(ch2, ch4);
        assertEquals(ch2, ch5);
        assertEquals(ch3, ch1);
        assertEquals(ch3, ch2);
        assertEquals(ch3, ch3);
        assertEquals(ch3, ch4);
        assertEquals(ch3, ch5);
        assertEquals(ch4, ch1);
        assertEquals(ch4, ch2);
        assertEquals(ch4, ch3);
        assertEquals(ch4, ch4);
        assertEquals(ch4, ch5);
        assertEquals(ch5, ch1);
        assertEquals(ch5, ch2);
        assertEquals(ch5, ch3);
        assertEquals(ch5, ch4);
        assertEquals(ch5, ch5);
    }

    @Test
    public void transformingSendChannelIsEqualToChannel() throws Exception {
        final Channel<Integer> ch = newChannel();
        final SendPort<Integer> ch1 = Channels.filterSend(ch, input -> input % 2 == 0);

        final SendPort<Integer> ch2 = Channels.mapSend(ch, input -> input + 10);

        final SendPort<Integer> ch3 = Channels.flatMapSend(Channels.newChannel(1), ch, input ->
                Channels.toReceivePort(Arrays.asList(input * 10, input * 100, input * 1000)));

        final SendPort<Integer> ch4 = Channels.reduceSend(ch, (accum, input) -> (accum += input), 0);

        assertEquals(ch1, ch);
        assertEquals(ch, ch1);
        assertEquals(ch2, ch);
        assertEquals(ch, ch2);
        assertEquals(ch3, ch);
        assertEquals(ch, ch3);
        assertEquals(ch4, ch);
        assertEquals(ch, ch4);

        assertEquals(ch1, ch1);
        assertEquals(ch1, ch2);
        assertEquals(ch1, ch3);
        assertEquals(ch1, ch4);
        assertEquals(ch2, ch1);
        assertEquals(ch2, ch2);
        assertEquals(ch2, ch3);
        assertEquals(ch2, ch4);
        assertEquals(ch3, ch1);
        assertEquals(ch3, ch2);
        assertEquals(ch3, ch3);
        assertEquals(ch3, ch4);
        assertEquals(ch4, ch1);
        assertEquals(ch4, ch2);
        assertEquals(ch4, ch3);
        assertEquals(ch4, ch4);
    }

    @Test
    public void testFilterFiberToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib1 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.filter(ch, input -> input % 2 == 0);

                final Integer m1 = ch1.receive();
                final Integer m2 = ch1.receive();
                final Integer m3 = ch1.receive();

                assertThat(m1, equalTo(2));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final co.paralleluniverse.fibers.Fiber fib2 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(50);
                ch.send(1);
                ch.send(2);
                co.paralleluniverse.strands.Strand.sleep(50);
                ch.send(3);
                ch.send(4);
                ch.send(5);
                ch.close();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        fib1.join();
        fib2.join();
    }

    @Test
    public void testFilterThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.filter(ch, input -> input % 2 == 0);

                final Integer m1 = ch1.receive();
                final Integer m2 = ch1.receive();
                final Integer m3 = ch1.receive();

                assertThat(m1, equalTo(2));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(1);
        ch.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(3);
        ch.send(4);
        ch.send(5);
        ch.close();

        fib.join();
    }

    @Test
    public void testFilterFiberToThread() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.fibers.Fiber.sleep(100);

                co.paralleluniverse.strands.Strand.sleep(50);
                ch.send(1);
                ch.send(2);
                co.paralleluniverse.strands.Strand.sleep(50);
                ch.send(3);
                ch.send(4);
                ch.send(5);
                ch.close();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final ReceivePort<Integer> ch1 = Channels.filter(ch, input -> input % 2 == 0);

        final Integer m1 = ch1.receive();
        final Integer m2 = ch1.receive();
        final Integer m3 = ch1.receive();

        assertThat(m1, equalTo(2));
        assertThat(m2, equalTo(4));
        assertThat(m3, is(nullValue()));

        fib.join();
    }

    @Test
    public void testFilterWithTimeouts() throws Exception {
        final Channel<Integer> ch = newChannel();

        final Channel<Object> sync = Channels.newChannel(0);

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.filter(ch, input -> input % 2 == 0);

                sync.receive(); // 0
                final Integer m1 = ch1.receive(200, TimeUnit.MILLISECONDS);

                sync.receive(); // 1
                final Integer m0 = ch1.receive(10, TimeUnit.MILLISECONDS);
                final Integer m2 = ch1.receive(190, TimeUnit.MILLISECONDS);
                final Integer m3 = ch1.receive(30, TimeUnit.MILLISECONDS);

                assertThat(m1, equalTo(2));
                assertThat(m0, is(nullValue()));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        sync.send(GO); // 0
        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(1, 10, TimeUnit.SECONDS); // Discarded (at receive side)
        ch.send(2, 10, TimeUnit.SECONDS);

        sync.send(GO); // 1
        co.paralleluniverse.strands.Strand.sleep(100);
        ch.send(3, 10, TimeUnit.SECONDS); // Discarded (at receive side)
        ch.send(4, 10, TimeUnit.SECONDS);
        ch.send(5, 10, TimeUnit.SECONDS); // Discarded (at receive side)

        ch.close();

        fib.join();
    }

    @Test
    public void testSendFilterFiberToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib1 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final Integer m1 = ch.receive();
                final Integer m2 = ch.receive();
                final Integer m3 = ch.receive();

                assertThat(m1, equalTo(2));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final co.paralleluniverse.fibers.Fiber fib2 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final SendPort<Integer> ch1 = Channels.filterSend(ch, input -> input % 2 == 0);

                co.paralleluniverse.strands.Strand.sleep(50);
                ch1.send(1);
                ch1.send(2);
                co.paralleluniverse.strands.Strand.sleep(50);
                ch1.send(3);
                ch1.send(4);
                ch1.send(5);
                ch1.close();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        fib1.join();
        fib2.join();
    }

    @Test
    public void testSendFilterThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final Integer m1 = ch.receive();
                final Integer m2 = ch.receive();
                final Integer m3 = ch.receive();

                assertThat(m1, equalTo(2));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.filterSend(ch, input -> input % 2 == 0);

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        ch1.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(3);
        ch1.send(4);
        ch1.send(5);
        ch1.close();

        fib.join();
    }

    @Test
    public void testSendFilterFiberToThread() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.fibers.Fiber.sleep(100);

                SendPort<Integer> ch1 = Channels.filterSend(ch, input -> input % 2 == 0);

                co.paralleluniverse.strands.Strand.sleep(50);
                ch1.send(1);
                ch1.send(2);
                co.paralleluniverse.strands.Strand.sleep(50);
                ch1.send(3);
                ch1.send(4);
                ch1.send(5);
                ch1.close();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final Integer m1 = ch.receive();
        final Integer m2 = ch.receive();
        final Integer m3 = ch.receive();

        assertThat(m1, equalTo(2));
        assertThat(m2, equalTo(4));
        assertThat(m3, is(nullValue()));

        fib.join();
    }

    @Test
    public void testSendFilterWithTimeouts() throws Exception {
        final Channel<Integer> ch = newChannel();

        final Channel<Object> sync = Channels.newChannel(0);

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                sync.receive(); // 0
                final Integer m1 = ch.receive(200, TimeUnit.MILLISECONDS);

                sync.receive(); // 1
                final Integer m0 = ch.receive(10, TimeUnit.MILLISECONDS);
                final Integer m2 = ch.receive(190, TimeUnit.MILLISECONDS);
                final Integer m3 = ch.receive(30, TimeUnit.MILLISECONDS);

                assertThat(m1, equalTo(2));
                assertThat(m0, is(nullValue()));
                assertThat(m2, equalTo(4));
                assertThat(m3, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.filterSend(ch, input -> input % 2 == 0);

        sync.send(GO); // 0
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1, 10, TimeUnit.SECONDS); // Discarded (at send side)
        ch1.send(2, 10, TimeUnit.SECONDS);

        sync.send(GO); // 1
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(3, 10, TimeUnit.SECONDS); // Discarded (at send side)
        ch1.send(4, 10, TimeUnit.SECONDS);
        ch1.send(5, 10, TimeUnit.SECONDS); // Discarded (at send side)

        ch1.close();

        fib.join();
    }

    @Test
    public void testMapThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.map(ch, input -> input + 10);

                final Integer m1 = ch1.receive();
                final Integer m2 = ch1.receive();
                final Integer m3 = ch1.receive();
                final Integer m4 = ch1.receive();
                final Integer m5 = ch1.receive();
                final Integer m6 = ch1.receive();

                assertThat(m1, equalTo(11));
                assertThat(m2, equalTo(12));
                assertThat(m3, equalTo(13));
                assertThat(m4, equalTo(14));
                assertThat(m5, equalTo(15));
                assertThat(m6, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(1);
        ch.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(3);
        ch.send(4);
        ch.send(5);
        ch.close();

        fib.join();
    }

    @Test
    public void testSendMapThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final Integer m1 = ch.receive();
                final Integer m2 = ch.receive();
                final Integer m3 = ch.receive();
                final Integer m4 = ch.receive();
                final Integer m5 = ch.receive();
                final Integer m6 = ch.receive();

                assertThat(m1, equalTo(11));
                assertThat(m2, equalTo(12));
                assertThat(m3, equalTo(13));
                assertThat(m4, equalTo(14));
                assertThat(m5, equalTo(15));
                assertThat(m6, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.mapSend(ch, input -> input + 10);

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        ch1.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(3);
        ch1.send(4);
        ch1.send(5);
        ch1.close();

        fib.join();
    }

    @Test
    public void testReduceThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.reduce(ch, (accum, input) -> accum + input, 0);

                final Integer m1 = ch1.receive();
                final Integer m2 = ch1.receive();
                final Integer m3 = ch1.receive();
                final Integer m4 = ch1.receive();
                final Integer m5 = ch1.receive();
                final Integer m6 = ch1.receive();

                assertThat(m1, equalTo(1));
                assertThat(m2, equalTo(3));
                assertThat(m3, equalTo(6));
                assertThat(m4, equalTo(10));
                assertThat(m5, equalTo(15));
                assertThat(m6, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(1);
        ch.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(3);
        ch.send(4);
        ch.send(5);
        ch.close();

        fib.join();
    }

    @Test
    public void testReduceInitThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch1 = Channels.reduce(ch, (accum, input) -> accum + input, 0);

                final Integer m1 = ch1.receive();
                final Integer m2 = ch1.receive();

                assertThat(m1, equalTo(0));
                assertNull(m2);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }

        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch.close();

        fib.join();
    }

    @Test
    @SuppressWarnings("null")
    public void testTakeThreadToFibers() throws Exception {
        assumeThat(mailboxSize, greaterThan(0)); // TODO Reorg to try with blocking channel at least meaningful parts

        final Channel<Object> takeSourceCh = newChannel();

        // Test 2 fibers failing immediately on take 0 of 1
        final ReceivePort<Object> take0RP = Channels.take(takeSourceCh, 0);
        final Runnable take0SR = () -> {
            try {
                assertThat(take0RP.receive(), is(nullValue()));
                assertThat(take0RP.tryReceive(), is(nullValue()));
                long start = System.nanoTime();
                assertThat(take0RP.receive(10, TimeUnit.SECONDS), is(nullValue()));
                long end = System.nanoTime();
                assertThat(end - start, lessThan((long) (5 * 1000 * 1000 * 1000))); // Should be immediate
                start = System.nanoTime();
                assertThat(take0RP.receive(new Timeout(10, TimeUnit.SECONDS)), is(nullValue()));
                end = System.nanoTime();
                assertThat(end - start, lessThan((long) (5 * 1000 * 1000 * 1000))); // Should be immediate
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
        final co.paralleluniverse.fibers.Fiber take0Of1Fiber1 = new co.paralleluniverse.fibers.Fiber("take-0-of-1_fiber1", scheduler, take0SR).start();
        final co.paralleluniverse.fibers.Fiber take0Of1Fiber2 = new co.paralleluniverse.fibers.Fiber("take-0-of-1_fiber2", scheduler, take0SR).start();
        takeSourceCh.send(new Object());
        take0Of1Fiber1.join();
        take0Of1Fiber2.join();
        assertThat(takeSourceCh.receive(), is(notNullValue())); // 1 left in source, check and cleanup

        // Test tryReceive failing immediately when fiber blocked in receive on take 1 of 2
        final ReceivePort<Object> take1Of2RP = Channels.take(takeSourceCh, 1);
        final co.paralleluniverse.fibers.Fiber timeoutSucceedingTake1Of2 = new co.paralleluniverse.fibers.Fiber("take-1-of-2_timeout_success", scheduler, () -> {
            try {
                final long start = System.nanoTime();
                assertThat(take1Of2RP.receive(1, TimeUnit.SECONDS), is(notNullValue()));
                final long end = System.nanoTime();
                assertThat(end - start, lessThan((long) (500 * 1000 * 1000)));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(100); // Let the fiber blocks in receive before starting the try
        final co.paralleluniverse.fibers.Fiber tryFailingTake1Of2 = new co.paralleluniverse.fibers.Fiber("take-1-of-2_try_fail", scheduler, () -> {
            try {
                final long start = System.nanoTime();
                assertThat(take1Of2RP.tryReceive(), is(nullValue()));
                final long end = System.nanoTime();
                assertThat(end - start, lessThan((long) (500 * 1000 * 1000))); // Should be immediate
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        Thread.sleep(100);
        // Make messages available
        takeSourceCh.send(new Object());
        takeSourceCh.send(new Object());
        timeoutSucceedingTake1Of2.join();
        tryFailingTake1Of2.join();
        assertThat(takeSourceCh.receive(), is(notNullValue())); // 1 left in source, check and cleanup

        // Comprehensive take + contention test:
        //
        // - 1 message available immediately, 2 messages available in a burst on the source after 1s
        // - take 2
        // - 5 fibers competing on the take source (1 in front)
        //
        // - one front fiber receiving with 200ms timeout => immediate success
        // - one more front fiber receiving with 200ms timeout => fail
        // - 3rd fiber taking over, receiving with 200ms timeout => fail
        // - 4th fiber taking over, receiving with 1s timeout => success
        // - 5th fiber asking untimed receive, waiting in monitor, will bail out because of take threshold

        final ReceivePort<Object> take2Of3RPComprehensive = Channels.take(takeSourceCh, 2);
        final Function2<Long, Integer, co.paralleluniverse.fibers.Fiber> take1SRFun = (timeoutMS, position) -> new co.paralleluniverse.fibers.Fiber("take-1-of-2_comprehensive_receiver_" + (timeoutMS >= 0 ? timeoutMS : "unlimited") + "ms-" + position, scheduler, (Runnable) () -> {
            try {
                final long start = System.nanoTime();
                final Object res =
                        (timeoutMS >= 0 ?
                                take2Of3RPComprehensive.receive(timeoutMS, TimeUnit.MILLISECONDS) :
                                take2Of3RPComprehensive.receive());
                final long end = System.nanoTime();
                switch (position) {
                    case 1:
                        assertThat(res, is(notNullValue()));
                        assertThat(end - start, lessThan((long) (300 * 1000 * 1000)));
                        break;
                    case 2:
                        assertThat(res, is(nullValue()));
                        assertThat(end - start, greaterThan((long) (300 * 1000 * 1000)));
                        break;
                    case 3:
                        assertThat(res, is(nullValue()));
                        assertThat(end - start, greaterThan((long) (200 * 1000 * 1000)));
                        break;
                    case 4:
                        assertThat(res, is(notNullValue()));
                        assertThat(end - start, lessThan((long) (1000 * 1000 * 1000)));
                        break;
                    case 5:
                        assertThat(res, is(nullValue()));
                        assertThat(end - start, lessThan((long) (1000 * 1000 * 1000))); // Should be almost instantaneous
                        break;
                    default:
                        fail();
                        break;
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        });
        final co.paralleluniverse.fibers.Fiber[] competing = new co.paralleluniverse.fibers.Fiber[5];
        // First front fiber winning first message
        competing[0] = take1SRFun.apply(300L, 1).start();
        // Make 1 message available immediately for the first front fiber to consume
        takeSourceCh.send(new Object());
        Thread.sleep(100);
        // Second front fiber losing (waiting too little for second message)
        competing[1] = take1SRFun.apply(300L, 2).start();
        Thread.sleep(100);
        // First waiter, will fail (not waiting enough)
        competing[2] = take1SRFun.apply(200L, 3).start();
        Thread.sleep(300); // First waiter takeover
        // Second waiter, will win second message (waiting enough)
        competing[3] = take1SRFun.apply(1000L, 4).start();
        Thread.sleep(300); // Second waiter takeover
        // Third waiter, will try after take threshold and will bail out
        competing[4] = take1SRFun.apply(-1L, 5).start();
        // Make 2 more messages available
        takeSourceCh.send(new Object());
        takeSourceCh.send(new Object());
        // Wait fibers to finsh
        for (final co.paralleluniverse.fibers.Fiber f : competing)
            f.join();
        assertThat(takeSourceCh.receive(), is(notNullValue())); // 1 left in source, check and cleanup

        // Explicit (and uncoupled from source) closing of TakeSP
        final ReceivePort<Object> take1Of0ExplicitClose = Channels.take(takeSourceCh, 1);
        final Runnable explicitCloseSR = () -> {
            try {
                final long start = System.nanoTime();
                final Object ret = take1Of0ExplicitClose.receive();
                final long end = System.nanoTime();
                assertThat(ret, is(nullValue()));
                assertTrue(take1Of0ExplicitClose.isClosed());
                assertFalse(takeSourceCh.isClosed());
                assertThat(end - start, lessThan((long) (500 * 1000 * 1000)));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };
        final co.paralleluniverse.fibers.Fiber explicitCloseF1 = new co.paralleluniverse.fibers.Fiber("take-explicit-close-1", scheduler, explicitCloseSR);
        final co.paralleluniverse.fibers.Fiber explicitCloseF2 = new co.paralleluniverse.fibers.Fiber("take-explicit-close-2", scheduler, explicitCloseSR);
        Thread.sleep(100);
        take1Of0ExplicitClose.close();
    }

    @Test
    public void testSendReduceThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final Integer m1 = ch.receive();
                final Integer m2 = ch.receive();
                final Integer m3 = ch.receive();
                final Integer m4 = ch.receive();
                final Integer m5 = ch.receive();
                final Integer m6 = ch.receive();

                assertThat(m1, equalTo(1));
                assertThat(m2, equalTo(3));
                assertThat(m3, equalTo(6));
                assertThat(m4, equalTo(10));
                assertThat(m5, equalTo(15));
                assertThat(m6, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.reduceSend(ch, (accum, input) -> accum + input, 0);

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        ch1.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(3);
        ch1.send(4);
        ch1.send(5);
        ch1.close();

        fib.join();
    }

    @Test
    public void testSendReduceInitThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final Integer m1 = ch.receive();
                final Integer m2 = ch.receive();

                assertThat(m1, equalTo(0));
                assertNull(m2);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.reduceSend(ch, (accum, input) -> accum + input, 0);

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.close();

        fib.join();
    }

    @Test
    public void testZipThreadToFiber() throws Exception {
        final Channel<String> ch1 = newChannel();
        final Channel<Integer> ch2 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<String> ch = Channels.zip(ch1, ch2, (x1, x2) -> x1 + x2);

                final String m1 = ch.receive();
                final String m2 = ch.receive();
                final String m3 = ch.receive();
                final String m4 = ch.receive();

                assertThat(m1, equalTo("a1"));
                assertThat(m2, equalTo("b2"));
                assertThat(m3, equalTo("c3"));
                assertThat(m4, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send("a");
        ch2.send(1);
        ch1.send("b");
        ch2.send(2);
        ch1.send("c");
        ch2.send(3);
        ch1.send("foo");
        ch2.close();
        fib.join();
    }

    @Test
    public void testZipWithTimeoutsThreadToFiber() throws Exception {
        final Channel<String> ch1 = newChannel();
        final Channel<Integer> ch2 = newChannel();

        final Channel<Object> sync = Channels.newChannel(0);

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<String> ch = Channels.zip(ch1, ch2, (x1, x2) -> x1 + x2);

                sync.receive(); // 0
                final String m1 = ch.receive(200, TimeUnit.MILLISECONDS);

                sync.receive(); // 1
                final String m0 = ch.receive(10, TimeUnit.MILLISECONDS);
                final String m2 = ch.receive(190, TimeUnit.MILLISECONDS);

                sync.receive(); // 2
                final String m3 = ch.receive(30, TimeUnit.MILLISECONDS);

                sync.receive(); // 3
                final String m4 = ch.receive(30, TimeUnit.MILLISECONDS);

                assertThat(m1, equalTo("a1"));
                assertThat(m0, is(nullValue()));
                assertThat(m2, equalTo("b2"));
                assertThat(m3, equalTo("c3"));
                assertThat(m4, is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        sync.send(GO); // 0
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send("a", 10, TimeUnit.SECONDS);
        ch2.send(1, 10, TimeUnit.SECONDS);

        sync.send(GO); // 1
        ch1.send("b", 10, TimeUnit.SECONDS);
        co.paralleluniverse.strands.Strand.sleep(100);
        ch2.send(2, 10, TimeUnit.SECONDS);

        sync.send(GO); // 2
        ch1.send("c", 10, TimeUnit.SECONDS);
        ch2.send(3, 10, TimeUnit.SECONDS);

        sync.send(GO); // 3
        ch1.send("foo", 10, TimeUnit.SECONDS);

        ch2.close(); // Discards previous

        fib.join();
    }

    @Test
    public void testFlatmapThreadToFiber() throws Exception {
        final Channel<Integer> ch1 = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch = Channels.flatMap(ch1, x -> {
                    if (x == 3)
                        return null;
                    if (x % 2 == 0)
                        return Channels.toReceivePort(Arrays.asList(x * 10, x * 100, x * 1000));
                    else
                        return Channels.singletonReceivePort(x);
                });

                assertThat(ch.receive(), is(1));
                assertThat(ch.receive(), is(20));
                assertThat(ch.receive(), is(200));
                assertThat(ch.receive(), is(2000));
                assertThat(ch.receive(), is(40));
                assertThat(ch.receive(), is(400));
                assertThat(ch.receive(), is(4000));
                assertThat(ch.receive(), is(5));
                assertThat(ch.receive(), is(nullValue()));
                assertThat(ch.isClosed(), is(true));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        ch1.send(2);
        ch1.send(3);
        ch1.send(4);
        ch1.send(5);
        ch1.close();
        fib.join();
    }

    @Test
    public void testFlatmapWithTimeoutsThreadToFiber() throws Exception {
        final Channel<Integer> ch1 = newChannel();

        final Channel<Object> sync = Channels.newChannel(0);

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                final ReceivePort<Integer> ch = Channels.flatMap(ch1, x -> {
                    if (x == 3)
                        return null; // Discard
                    if (x % 2 == 0)
                        return Channels.toReceivePort(Arrays.asList(new Integer[]{x * 10, x * 100, x * 1000}));
                    else
                        return Channels.singletonReceivePort(x);
                });

                sync.receive(); // 0
                assertThat(ch.receive(200, TimeUnit.MILLISECONDS), is(1));

                sync.receive(); // 1
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(nullValue()));
                assertThat(ch.receive(190, TimeUnit.MILLISECONDS), is(20));
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(200));
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(2000));

                sync.receive(); // 2
                assertThat(ch.receive(200, TimeUnit.MILLISECONDS), is(40));
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(400));
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(4000));

                sync.receive(); // 3
                assertThat(ch.receive(10, TimeUnit.MILLISECONDS), is(nullValue()));
                assertThat(ch.receive(190, TimeUnit.MILLISECONDS), is(5));

                sync.receive(); // 4
                assertThat(ch.receive(30, TimeUnit.MILLISECONDS), is(nullValue()));
                assertThat(ch.isClosed(), is(true));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        sync.send(GO); // 0
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1, 10, TimeUnit.SECONDS);

        sync.send(GO); // 1
        co.paralleluniverse.strands.Strand.sleep(100);
        ch1.send(2, 10, TimeUnit.SECONDS);

        sync.send(GO); // 2
        ch1.send(3, 10, TimeUnit.SECONDS); // Discarded
        ch1.send(4, 10, TimeUnit.SECONDS);

        sync.send(GO); // 3
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(5, 10, TimeUnit.SECONDS);

        sync.send(GO); // 4
        ch1.close();

        fib.join();
    }

    @Test
    public void testFiberTransform1() throws Exception {
        final Channel<Integer> in = newChannel();
        final Channel<Integer> out = newChannel();

        Channels.fiberTransform(in, out, (Action2<ReceivePort<Integer>, SendPort<Integer>>) (in1, out1) -> {
            Integer x;
            while ((x = in1.receive()) != null) {
                if (x % 2 == 0)
                    out1.send(x * 10);
            }
            out1.send(1234);
            out1.close();
        });

        final co.paralleluniverse.fibers.Fiber fib1 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                assertThat(out.receive(), equalTo(20));
                assertThat(out.receive(), equalTo(40));
                assertThat(out.receive(), equalTo(1234));
                assertThat(out.receive(), is(nullValue()));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final co.paralleluniverse.fibers.Fiber fib2 = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                co.paralleluniverse.strands.Strand.sleep(50);
                in.send(1);
                in.send(2);
                co.paralleluniverse.strands.Strand.sleep(50);
                in.send(3);
                in.send(4);
                in.send(5);
                in.close();
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        fib1.join();
        fib2.join();
    }

    @Test
    public void testFlatmapSendThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                assertThat(ch.receive(), is(1));
                assertThat(ch.receive(), is(20));
                assertThat(ch.receive(), is(200));
                assertThat(ch.receive(), is(2000));
                assertThat(ch.receive(), is(40));
                assertThat(ch.receive(), is(400));
                assertThat(ch.receive(), is(4000));
                assertThat(ch.receive(), is(5));
                assertThat(ch.receive(), is(nullValue()));
                assertThat(ch.isClosed(), is(true));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.flatMapSend(Channels.newChannel(1), ch, x -> {
            if (x == 3)
                return null;
            if (x % 2 == 0)
                return Channels.toReceivePort(Arrays.asList(x * 10, x * 100, x * 1000));
            else
                return Channels.singletonReceivePort(x);
        });
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        ch1.send(2);
        ch1.send(3);
        ch1.send(4);
        ch1.send(5);
        ch1.close();
        fib.join();
    }

    @Test
    public void testFlatmapSendWithTimeoutsThreadToFiber() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber fib = new co.paralleluniverse.fibers.Fiber("fiber", scheduler, () -> {
            try {
                assertThat(ch.receive(), is(1));
                assertThat(ch.receive(30, TimeUnit.MILLISECONDS), is(nullValue()));
                assertThat(ch.receive(40, TimeUnit.MILLISECONDS), is(20));
                assertThat(ch.receive(), is(200));
                assertThat(ch.receive(), is(2000));
                assertThat(ch.receive(), is(40));
                assertThat(ch.receive(), is(400));
                assertThat(ch.receive(), is(4000));
                assertThat(ch.receive(30, TimeUnit.MILLISECONDS), is(nullValue()));
                assertThat(ch.receive(40, TimeUnit.MILLISECONDS), is(5));
                assertThat(ch.receive(), is(nullValue()));
                assertThat(ch.isClosed(), is(true));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        final SendPort<Integer> ch1 = Channels.flatMapSend(Channels.<Integer>newChannel(1), ch, x -> {
            if (x == 3)
                return null;
            if (x % 2 == 0)
                return Channels.toReceivePort(Arrays.asList(x * 10, x * 100, x * 1000));
            else
                return Channels.singletonReceivePort(x);
        });

        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(1);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(2);
        ch1.send(3);
        ch1.send(4);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch1.send(5);
        ch1.close();
        fib.join();
    }

    @Test
    public void testSendSplitThreadToFiber() throws Exception {
        final Channel<String> chF1 = newChannel();
        final Channel<String> chF2 = newChannel();
        final SendPort<String> splitSP = new SplitSendPort<>() {
            @Override
            protected SendPort select(final String m) {
                if (m.equals("f1"))
                    return chF1;
                else
                    return chF2;
            }
        };
        final co.paralleluniverse.fibers.Fiber f1 = new co.paralleluniverse.fibers.Fiber("split-send-1", scheduler, () -> {
            try {
                assertThat(chF1.receive(), is("f1"));
                assertThat(chF1.receive(100, TimeUnit.NANOSECONDS), is(nullValue()));
                assertThat(chF1.receive(new Timeout(100, TimeUnit.NANOSECONDS)), is(nullValue()));
                assertThat(chF1.receive(), is(nullValue()));
                assertTrue(chF1.isClosed());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
        final co.paralleluniverse.fibers.Fiber f2 = new co.paralleluniverse.fibers.Fiber("split-send-2", scheduler, () -> {
            try {
                assertThat(chF2.receive(), is(not("f1")));
                assertThat(chF2.receive(100, TimeUnit.NANOSECONDS), is(nullValue()));
                assertThat(chF2.receive(new Timeout(100, TimeUnit.NANOSECONDS)), is(nullValue()));
                assertThat(chF2.receive(), is(nullValue()));
                assertTrue(chF2.isClosed());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        splitSP.send("f1");
        splitSP.send("f2");

        splitSP.close();
        assertFalse(chF1.isClosed());
        assertFalse(chF2.isClosed());
        Thread.sleep(100);
        chF1.close();
        chF2.close();
        f1.join();
        f2.join();
    }

    @Test
    public void testForEach() throws Exception {
        final Channel<Integer> ch = newChannel();

        final co.paralleluniverse.fibers.Fiber<List<Integer>> fib = new co.paralleluniverse.fibers.Fiber<List<Integer>>("fiber", scheduler, () -> {
            final List<Integer> list = new ArrayList<>();

            Channels.transform(ch).forEach(list::add);

            return list;
        }).start();

        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(1);
        ch.send(2);
        co.paralleluniverse.strands.Strand.sleep(50);
        ch.send(3);
        ch.send(4);
        ch.send(5);
        ch.close();

        List<Integer> list = fib.get();
        assertThat(list, equalTo(Arrays.asList(1, 2, 3, 4, 5)));
    }
}
