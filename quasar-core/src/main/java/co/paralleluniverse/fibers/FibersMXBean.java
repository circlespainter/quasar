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
package co.paralleluniverse.fibers;

/**
 * An MXBean that monitors fibers scheduled by a single {@link java.util.concurrent.Executor}.
 *
 * @author pron
 */
public interface FibersMXBean {
    void refresh();

    /**
     * The number of non-terminated fibers in the scheduler.
     */
    int getNumActiveFibers();

    /**
     * The number of fibers currently in the {@link State#RUNNING RUNNING} state.
     */
    int getNumRunnableFibers();

    /**
     * The number of fibers that are currently blocking.
     */
    int getNumWaitingFibers();

    long getSpuriousWakeups();

    /**
     * The average latency between the time fibers in the scheduler have asked to be awakened (by a {@code sleep} or any other timed wait)
     * and the time they've actually been awakened in the last 5 seconds.
     */
    long getMeanTimedWakeupLatency();

    /**
     * The IDs of all fibers in the scheduler. {@code null} if the scheduler has been constructed with {@code detailedInfo} equal to {@code false}.
     */
    long[] getAllFiberIds();
}
