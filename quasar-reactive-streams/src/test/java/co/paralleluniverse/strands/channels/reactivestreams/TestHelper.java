/*
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
package co.paralleluniverse.strands.channels.reactivestreams;

import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.channels.SendPort;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Calendar;
import java.util.concurrent.Callable;

public class TestHelper {
    public static <T extends SendPort<Integer>> T startPublisherFiber(final T s, final long delay, final long elements) {
        new co.paralleluniverse.fibers.Fiber<Void>(() -> {
            try {
                if (delay > 0) {
                    Strand.sleep(delay);
                }

                // we only emit up to 100K elements or 100ms, the later of the two (the TCK asks for 2^31-1)
                long start = elements > 100_000 ? System.nanoTime() : 0L;
                for (long i = 0; i < elements; i++) {
                    s.send((int) (i % 10000));

                    if (start > 0) {
                        long elapsed = (System.nanoTime() - start) / 1_000_000;
                        if (elapsed > 100)
                            break;
                    }
                }
                s.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        return s;
    }

    public static <T extends SendPort<Integer>> T startFailedPublisherFiber(final T s, final long delay) {
        new co.paralleluniverse.fibers.Fiber<Void>(() -> {
            if (delay > 0)
                Strand.sleep(delay);
            s.close(new Exception("failure"));
            return null;
        }).start();
        return s;
    }
    
    public static <T> Publisher<T> createDummyFailedPublisher() {
        return s -> {
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            s.onError(new RuntimeException("Can't subscribe subscriber: " + s + ", because of reasons."));
        };
    }
}
