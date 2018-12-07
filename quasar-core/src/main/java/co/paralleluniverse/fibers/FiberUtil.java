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

import co.paralleluniverse.common.util.Exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Static utility methods for working with fibers.
 *
 * @author pron
 */
public final class FiberUtil {
    /**
     * Runs an action in a new fiber, awaits the fiber's termination, and returns its result.
     * The new fiber is scheduled by the {@link DefaultFiberScheduler default scheduler}.
     *
     * @param <V>
     * @param target the operation
     * @return the operations return value
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static <V> V runInFiber(Callable<V> target) throws ExecutionException, InterruptedException {
        return runInFiber(DefaultFiberScheduler.getInstance(), target);
    }

    /**
     * Runs an action in a new fiber, awaits the fiber's termination, and returns its result.
     *
     * @param <V>
     * @param scheduler the {@link Executor} to use when scheduling the fiber.
     * @param target    the operation
     * @return the operations return value
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static <V> V runInFiber(Executor scheduler, Callable<V> target) throws ExecutionException, InterruptedException {
        return new co.paralleluniverse.fibers.Fiber<>(scheduler, target).start().get();
    }

    /**
     * Runs an action in a new fiber and awaits the fiber's termination.
     * The new fiber is scheduled by the {@link DefaultFiberScheduler default scheduler}.
     * .
     *
     * @param target the operation
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void runInFiber(Runnable target) throws ExecutionException, InterruptedException {
        runInFiber(DefaultFiberScheduler.getInstance(), target);
    }

    /**
     * Runs an action in a new fiber and awaits the fiber's termination.
     *
     * @param scheduler the {@link Executor} to use when scheduling the fiber.
     * @param target    the operation
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public static void runInFiber(Executor scheduler, Runnable target) throws ExecutionException, InterruptedException {
        new co.paralleluniverse.fibers.Fiber<Void>(scheduler, target).start().join();
    }

    /**
     * Runs an action in a new fiber, awaits the fiber's termination, and returns its result.
     * Unlike {@link #runInFiber(Callable) runInFiber} this method does not throw {@link ExecutionException}, but wraps
     * any checked exception thrown by the operation in a {@link RuntimeException}.
     * The new fiber is scheduled by the {@link DefaultFiberScheduler default scheduler}.
     *
     * @param <V>
     * @param target the operation
     * @return the operations return value
     * @throws InterruptedException
     */
    public static <V> V runInFiberRuntime(Callable<V> target) throws InterruptedException {
        return runInFiberRuntime(DefaultFiberScheduler.getInstance(), target);
    }

    /**
     * Runs an action in a new fiber, awaits the fiber's termination, and returns its result.
     * Unlike {@link #runInFiber(Executor, Callable) runInFiber} this method does not throw {@link ExecutionException}, but wraps
     * any checked exception thrown by the operation in a {@link RuntimeException}.
     *
     * @param <V>
     * @param scheduler the {@link Executor} to use when scheduling the fiber.
     * @param target    the operation
     * @return the operations return value
     * @throws InterruptedException
     */
    public static <V> V runInFiberRuntime(Executor scheduler, Callable<V> target) {
        return new co.paralleluniverse.fibers.Fiber<V>(scheduler, target).start().get();
    }

    /**
     * Runs an action in a new fiber and awaits the fiber's termination.
     * Unlike {@link #runInFiber(Executor, Runnable)   runInFiber} this method does not throw {@link ExecutionException}, but wraps
     * any checked exception thrown by the operation in a {@link RuntimeException}.
     *
     * @param scheduler the {@link Executor} to use when scheduling the fiber.
     * @param target    the operation
     * @throws InterruptedException
     */
    public static void runInFiberRuntime(Executor scheduler, Runnable target) {
        new Fiber<Void>(scheduler, target).start().join();
    }
    /**
     * Blocks on the input fibers and creates a new list from the results. The result list is the same order as the
     * input list.
     *
     * @param fibers to combine
     */
    public static <V> List<V> get(final List<Fiber<V>> fibers) throws InterruptedException {
        final List<V> results = new ArrayList<>(fibers.size());

        //TODO on interrupt, should all input fibers be canceled?
        for (final Fiber<V> f : fibers) {
            results.add(f.get());
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * Blocks on the input fibers and creates a new list from the results. The result list is the same order as the
     * input list.
     *
     * @param fibers to combine
     */
    public static <V> List<V> get(Fiber<V>... fibers) throws InterruptedException {
        return get(Arrays.asList(fibers));
    }

    /**
     * Blocks on the input fibers and creates a new list from the results. The result list is the same order as the
     * input list.
     *
     * @param timeout to wait for all requests to complete
     * @param unit    the time is in
     * @param fibers  to combine
     */
    public static <V> List<V> get(long timeout, TimeUnit unit, List<Fiber<V>> fibers) throws InterruptedException, TimeoutException {
        if (unit == null)
            return get(fibers);
        if (timeout < 0)
            timeout = 0;

        final List<V> results = new ArrayList<>(fibers.size());

        long left = unit.toNanos(timeout);
        final long deadline = System.nanoTime() + left;

        //TODO on interrupt, should all input fibers be canceled?
        for (final Fiber<V> f : fibers) {
            if (left >= 0) {
                results.add(f.get(left, TimeUnit.NANOSECONDS));
                left = deadline - System.nanoTime();
            } else
                throw new TimeoutException("timed out sequencing fiber results");
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Blocks on the input fibers and creates a new list from the results. The result list is the same order as the
     * input list.
     *
     * @param time   to wait for all requests to complete
     * @param unit   the time is in
     * @param fibers to combine
     */
    public static <V> List<V> get(final long time, final TimeUnit unit, final Fiber<V>... fibers) throws InterruptedException, TimeoutException {
        return get(time, unit, Arrays.asList(fibers));
    }

    private static <V, X extends Exception> RuntimeException throwChecked(ExecutionException ex, Class<X> exceptionType) throws X {
        Throwable t = Exceptions.unwrap(ex);
        if (exceptionType.isInstance(t))
            throw exceptionType.cast(t);
        else
            throw Exceptions.rethrow(t);
    }

    private FiberUtil() {
    }
}
