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
package co.paralleluniverse.strands;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author pron
 */
public class SimpleConditionSynchronizer extends ConditionSynchronizer implements Condition {
    private final Queue<Strand> waiters = new ConcurrentLinkedQueue<>();

    public SimpleConditionSynchronizer(Object owner) {
        super(owner);
    }

    @Override
    public Object register() {
        final Strand currentStrand = Strand.currentStrand();
        record("register", "%s register %s", this, currentStrand);
        waiters.add(currentStrand);
        return null;
    }

    @Override
    public void unregister(Object registrationToken) {
        final Strand currentStrand = Strand.currentStrand();
        record("unregister", "%s unregister %s", this, currentStrand);
        if (!waiters.remove(currentStrand))
            throw new IllegalMonitorStateException();
    }

    @Override
    public void signalAll() {
        for (co.paralleluniverse.strands.Strand s : waiters) {
            record("signalAll", "%s signalling %s", this, s);
            co.paralleluniverse.strands.Strand.unpark(s);
        }
    }

    @Override
    public void signal() {
        /*
         * We must wake up the first waiter that is actually parked. Otherwise, by the time the awakened waiter calls
         * unregister(), another one may block, and we may need to wake that one.
         */
        for (final co.paralleluniverse.strands.Strand s : waiters) {
            if (s.isFiber()) {
                co.paralleluniverse.fibers.Fiber.unpark(s);
                record("signal", "%s signalled %s", this, s);
            } else {
                // TODO: We can't tell (atomically) if a thread is actually parked, so we'll wake them all up.
                // We may consider a more complex solution, a-la AbstractQueuedSynchronizer for threads
                // (i.e. with a wrapper node, containing the state)
                record("signal", "%s signalling %s", this, s);
                co.paralleluniverse.strands.Strand.unpark(s);
            }
        }
    }
}
