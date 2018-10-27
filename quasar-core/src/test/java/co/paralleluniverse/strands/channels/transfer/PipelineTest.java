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
package co.paralleluniverse.strands.channels.transfer;

import co.paralleluniverse.common.test.TestUtil;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.Channels.OverflowPolicy;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

/**
 *
 * @author circlespainter
 */
@RunWith(Parameterized.class)
public class PipelineTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private final int mailboxSize;
    private final OverflowPolicy policy;
    private final boolean singleConsumer;
    private final boolean singleProducer;
    private final ExecutorService scheduler;
    private final int parallelism;

    public PipelineTest(final int mailboxSize, final OverflowPolicy policy, final boolean singleConsumer, final boolean singleProducer, final int parallelism) {
        scheduler = Executors.newWorkStealingPool();
        this.mailboxSize = mailboxSize;
        this.policy = policy;
        this.singleConsumer = singleConsumer;
        this.singleProducer = singleProducer;
        this.parallelism = parallelism;
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {5, OverflowPolicy.THROW, true, false, 0},
            {5, OverflowPolicy.THROW, false, false, 0},
            {5, OverflowPolicy.BLOCK, true, false, 0},
            {5, OverflowPolicy.BLOCK, false, false, 0},
            {1, OverflowPolicy.BLOCK, false, false, 0},
            {-1, OverflowPolicy.THROW, true, false, 0},
            {5, OverflowPolicy.DISPLACE, true, false, 0},
            {0, OverflowPolicy.BLOCK, false, false, 0},

            {5, OverflowPolicy.THROW, true, false, 2},
            {5, OverflowPolicy.THROW, false, false, 2},
            {5, OverflowPolicy.BLOCK, true, false, 2},
            {5, OverflowPolicy.BLOCK, false, false, 2},
            {1, OverflowPolicy.BLOCK, false, false, 2},
            {-1, OverflowPolicy.THROW, true, false, 2},
            {5, OverflowPolicy.DISPLACE, true, false, 2},
            {0, OverflowPolicy.BLOCK, false, false, 2},
        });
    }

    private <Message> Channel<Message> newChannel() {
        return Channels.newChannel(mailboxSize, policy, singleProducer, singleConsumer);
    }

    @Test
    public void testPipeline() throws Exception {
        final Channel<Integer> i = newChannel();
        final Channel<Integer> o = newChannel();
        final Pipeline<Integer, Integer> p =
            new Pipeline<>(
                i, o,
                    (i1, out) -> {
                        out.send(i1 + 1);
                        out.close();
                    },
                parallelism);
        final co.paralleluniverse.fibers.Fiber<Long> pf = new co.paralleluniverse.fibers.Fiber<>("pipeline", scheduler, p).start();

        final co.paralleluniverse.fibers.Fiber<Void> receiver = new co.paralleluniverse.fibers.Fiber<Void>("receiver", scheduler, () -> {
            final Integer m1 = o.receive();
            final Integer m2 = o.receive();
            final Integer m3 = o.receive();
            final Integer m4 = o.receive();
            assertThat(m1, notNullValue());
            assertThat(m2, notNullValue());
            assertThat(m3, notNullValue());
            assertThat(m4, notNullValue());
            assertThat(ImmutableSet.of(m1, m2, m3, m4), equalTo(ImmutableSet.of(2, 3, 4, 5)));
            try {
                pf.join();
            } catch (ExecutionException ex) {
                // It should never happen
                throw new AssertionError(ex);
            }
            assertNull(o.tryReceive()); // This is needed, else `isClosed` could return false
            assertTrue(o.isClosed()); // Can be used reliably only in owner (receiver)
            return null;
        }).start();

        i.send(1);
        i.send(2);
        i.send(3);
        i.send(4);

        i.close();

        long transferred = pf.get(); // Join pipeline
        assertThat(transferred, equalTo(p.getTransferred()));
        assertThat(transferred, equalTo(4L));

        receiver.join();
    }
}
