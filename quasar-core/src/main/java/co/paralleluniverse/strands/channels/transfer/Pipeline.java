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

import co.paralleluniverse.common.util.Action2;
import co.paralleluniverse.common.util.Pair;
import co.paralleluniverse.fibers.DefaultFiberScheduler;
import co.paralleluniverse.strands.DefaultStrandFactory;
import co.paralleluniverse.strands.RunnableCallableUtils;
import co.paralleluniverse.strands.StrandFactory;
import co.paralleluniverse.strands.channels.Channel;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.channels.ReceivePort;
import co.paralleluniverse.strands.channels.SendPort;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author circlespainter
 */
public class Pipeline<S, T> implements Callable<Long> {
    private static final boolean closeToDefault = true;
    private static final int parallelismDefault = 0;
    private static final StrandFactory strandFactoryDefault = DefaultStrandFactory.getInstance();

    private final Callable<Channel<T>> resultChannelBuilderDefault =
            () -> Channels.newChannel(1, Channels.OverflowPolicy.BLOCK, true, true);

    private final Channel<Pair<S, Channel<Channel<T>>>> jobs;
    private final Channel<Channel<Channel<T>>> results;

    private final ReceivePort<? extends S> from;
    private final SendPort<? super T> to;
    private final int parallelism;
    private final StrandFactory strandFactory;
    private final boolean closeTo;

    private final AtomicLong transferred = new AtomicLong(0);

    private final Callable<Channel<T>> resultChannelBuilder;
    private final Action2<S, Channel<T>> transformer;

    public Pipeline(final ReceivePort<? extends S> from, final SendPort<? super T> to, final Action2<S, Channel<T>> transformer, final int parallelism, boolean closeTo, final Callable<Channel<T>> resultChannelBuilder, final StrandFactory strandFactory) {
        this.from = from;
        this.to = to;
        this.transformer = transformer;
        this.parallelism = parallelism <= 0 ? 0 : parallelism;
        this.jobs = Channels.newChannel(this.parallelism, Channels.OverflowPolicy.BLOCK, true, false);
        this.results = Channels.newChannel(this.parallelism, Channels.OverflowPolicy.BLOCK, false, true);
        this.closeTo = closeTo;
        this.resultChannelBuilder = resultChannelBuilder != null ? resultChannelBuilder : resultChannelBuilderDefault;
        this.strandFactory = strandFactory;
    }

    public Pipeline(final ReceivePort<? extends S> from, final SendPort<? super T> to, final Action2<S, Channel<T>> transformer, final int parallelism, boolean closeTo, final Callable<Channel<T>> resultChannelBuilder) {
            this(from, to, transformer, parallelism, closeTo, resultChannelBuilder, strandFactoryDefault);
    }
    
    public Pipeline(final ReceivePort<? extends S> from, final SendPort<? super T> to, final Action2<S, Channel<T>> transformer, final int parallelism, boolean closeTo) {
        this(from, to, transformer, parallelism, closeTo, null, strandFactoryDefault);
    }

    public Pipeline(final ReceivePort<? extends S> from, final SendPort<? super T> to, final Action2<S, Channel<T>> transformer, final int parallelism) {
        this(from, to, transformer, parallelism, closeToDefault, null, strandFactoryDefault);
    }

    public Pipeline(final ReceivePort<? extends S> from, final SendPort<? super T> to, final Action2<S, Channel<T>> transformer) {
        this(from, to, transformer, parallelismDefault, closeToDefault, null, strandFactoryDefault);
    }

    public long getTransferred() {
        return transferred.get();
    }

    @Override
    public Long call() throws Exception {
        if (parallelism == 0)
            sequentialTransfer();
        else
            parallelTransfer();

        if (closeTo)
            to.close();

        return transferred.get();
    }

    private void parallelTransfer() throws InterruptedException {

        // 1) Fire workers
        for(int i = 0 ; i < parallelism ; i++) {
            strandFactory.newStrand((Callable<Void>) () -> {
                // Get first job
                Pair<S, Channel<Channel<T>>> job = jobs.receive();
                while(job != null) {
                    // Build result channel
                    final Channel<T> res = resultChannelBuilder.call();
                    // Process
                    transformer.call(job.getFirst(), res);
                    final Channel<Channel<T>> resWrapper = job.getSecond();
                    // Send result asynchronously
                    strandFactory.newStrand((Callable<Void>) () -> {
                        resWrapper.send(res);
                        return null;
                    }).start();
                    // Get next job
                    job = jobs.receive();
                }
                // No more jobs, close results channel and quit worker
                results.close();
                return null;
            }).start();
        }

        // 2) Send jobs asynchronously
        strandFactory.newStrand((Callable<Void>) () -> {
            // Get first input
            S s = from.receive();
            while (s != null) {
                final Channel<Channel<T>> resultWrapper = Channels.newChannel(1, Channels.OverflowPolicy.BLOCK, true, true);
                jobs.send(new Pair<>(s, resultWrapper));
                results.send(resultWrapper);
                // Get next input
                s = from.receive();
            }
            // No more inputs, close jobs channel and quit
            jobs.close();
            return null;
        }).start();

        // 3) Collect and transfer results asynchronously
        try {
            final co.paralleluniverse.strands.Strand collector = strandFactory.newStrand((Callable<Void>) () -> {
                Channel<Channel<T>> resWrapper = results.receive();
                while (resWrapper != null) {
                    // Get wrapper
                    Channel<T> res = resWrapper.receive();
                    // Get first actual result
                    T out = res.receive();
                    while(out != null) {
                        // Send to output channel
                        to.send(out);
                        // Increment counter
                        transferred.incrementAndGet();
                        // Get next result
                        out = res.receive();
                    }
                    resWrapper = results.receive();
                }
                return null;
            }).start();

            if (collector.isFiber()) {
                co.paralleluniverse.fibers.Fiber f = (co.paralleluniverse.fibers.Fiber) collector.getUnderlying();
                f.join();
            } else
                collector.join();
        } catch (ExecutionException ee) {
            throw new AssertionError(ee);
        }
    }

    private void sequentialTransfer() throws Exception {
        S s = from.receive();
        while (s != null) {
            // Build result channel
            final Channel<T> res = resultChannelBuilder.call();
            // Process
            transformer.call(s, res);
            T out = res.receive();
            while(out != null) {
                // Send to output channel
                to.send(out);
                // Increment counter
                transferred.incrementAndGet();
                // Get next result
                out = res.receive();
            }
            s = from.receive();
        }
    }
}
