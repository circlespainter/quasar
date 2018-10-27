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

import co.paralleluniverse.common.util.CheckedCallable;
import co.paralleluniverse.strands.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * A general helper class that transforms asynchronous requests to synchronous (fiber-blocking) calls.
 *
 * <h3>Usage example</h3>
 *
 * Assume that operation {@code Foo.asyncOp(FooCompletion callback)} is an asynchronous operation, where {@code Completion} is defined as:
 *
 * <pre>{@code
 * interface FooCompletion {
 *     void success(String result);
 *     void failure(FooException exception);
 * }
 * }</pre>
 *
 * We then define the following subclass:
 *
 * <pre>{@code
 * class FooAsync extends FiberAsync<String, FooException> implements FooCompletion {
 *     {@literal @}Override
 *     public void success(String result) {
 *         asyncCompleted(result);
 *     }
 *
 *     {@literal @}Override
 *     public void failure(FooException exception) {
 *         asyncFailed(exception);
 *     }
 * }
 * }</pre>
 *
 * Then, to turn the operation into a fiber-blocking one, we can define:
 *
 * <pre>{@code
 * String op() {
 *     return new FooAsync() {
 *         protected void requestAsync() {
 *             Foo.asyncOp(this);
 *         }
 *     }.run();
 * }
 * }</pre>
 *
 * @param <V> The value returned by the async request
 * @param <E> An exception class that could be thrown by the async request
 *
 * @author pron
 */
public abstract class FiberAsync<V, E extends Throwable> implements java.io.Serializable {
    private final Fiber fiber;
    private volatile boolean completed;
    private Throwable exception;
    private V result;
    private transient volatile boolean registrationComplete;
    private long timeoutNanos;
    private transient long deadline;

    public FiberAsync() {
        this.fiber = Fiber.currentFiber();
    }

    /**
     * Runs the asynchronous operation, blocks until it completes and returns its result. Throws an exception if the operation has failed.
     * <p/>
     * In immediate exec mode, when this method returns we are running within the handler, and will need to call {@link Fiber#yield()}
     * to return from the handler.
     *
     * @return the result of the async operation as set in the call to {@link #asyncCompleted(java.lang.Object) asyncCompleted}.
     * @throws E                    if the async computation failed and an exception was set in a call to {@link #asyncFailed(java.lang.Throwable) asyncFailed}.
     * @throws InterruptedException
     */
    @SuppressWarnings("empty-statement")
    public V run() throws E, InterruptedException {
        if (fiber == null)
            return runSync();

        if (registrationComplete)
            throw new IllegalStateException("This FiberAsync instance has already been used");

        fiber.record(1, "FiberAsync", "run", "Blocking fiber %s on FiberAsync %s", fiber, this);
        register();

        while (!completed) { // the fiber can be awakened spuriously, in particular, from calls to getStackTrace
            checkInterrupted();
            Fiber.park(this);
        }

        return getResult();
    }

    /**
     * Runs the asynchronous operation, blocks until it completes (but only up to the given timeout duration) and returns its result.
     * Throws an exception if the operation has failed.
     * <p/>
     * In immediate exec mode, when this method returns we are running within the handler, and will need to call {@link Fiber#yield()}
     * to return from the handler.
     *
     * @param timeout the maximum duration to wait for the result
     * @param unit    {@code timeout}'s time unit
     * @return the result of the async operation as set in the call to {@link #asyncCompleted(java.lang.Object) asyncCompleted}.
     * @throws E                    if the async computation failed and an exception was set in a call to {@link #asyncFailed(java.lang.Throwable) asyncFailed}.
     * @throws TimeoutException     if the operation had not completed by the time the timeout has elapsed.
     * @throws InterruptedException
     */
    @SuppressWarnings("empty-statement")
    public V run(final long timeout, final TimeUnit unit) throws E, InterruptedException, TimeoutException {
        if (Fiber.currentFiber() == null)
            return runSync(timeout, unit);

        if (registrationComplete)
            throw new IllegalStateException("This FiberAsync instance has already been used");

        if (unit == null)
            return run();
        if (timeout <= 0)
            throw new TimeoutException();

        this.timeoutNanos = unit.toNanos(timeout);
        this.deadline = System.nanoTime() + timeoutNanos;

        fiber.record(1, "FiberAsync", "run", "Blocking fiber %s on FibeAsync %s", fiber, this);
        register();

        if (timeoutNanos > 0 && deadline == 0) // must have been deserialized
            this.deadline = System.nanoTime() + timeoutNanos;

        while (!completed) { // the fiber can be awakened spuriously, in particular, from calls to getStackTrace
            checkInterrupted();

            final long now = System.nanoTime();
            if (now >= deadline) {
                exception = new TimeoutException();
                completed = true;
                fiber.record(1, "FiberAsync", "run", "FibeAsync %s on fiber %s has timed out", this, fiber);
                throw (TimeoutException) exception;
            }
            Fiber.parkNanos(this, deadline - now);
        }

        return getResult();
    }

    private void register() {
        try {
            fiber.record(1, "FiberAsync", "run", "Calling requestAsync on class %s", this);
            requestAsync();
            fiber.record(1, "FiberAsync", "run", "requestAsync on %s done", FiberAsync.this);
        } catch (Throwable t) {
            asyncFailed(t);
        } finally {
            registrationComplete = true;
        }
    }

    private void checkInterrupted() throws InterruptedException {
        if (Fiber.interrupted()) {
            interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Runs the asynchronous operation, blocks until it completes (but only up to the given timeout duration) and returns its result.
     * Throws an exception if the operation has failed.
     * <p/>
     * In immediate exec mode, when this method returns we are running within the handler, and will need to call {@link Fiber#yield()}
     * to return from the handler.
     *
     * @param timeout the method will not block for longer than the amount remaining in the {@link Timeout}
     * @return the result of the async operation as set in the call to {@link #asyncCompleted(java.lang.Object) asyncCompleted}.
     * @throws E                    if the async computation failed and an exception was set in a call to {@link #asyncFailed(java.lang.Throwable) asyncFailed}.
     * @throws TimeoutException     if the operation had not completed by the time the timeout has elapsed.
     * @throws InterruptedException
     */
    public V run(Timeout timeout) throws E, InterruptedException, TimeoutException {
        return run(timeout.nanosLeft(), TimeUnit.NANOSECONDS);
    }

    private V runSync(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException, E {
        try {
            return requestSync(timeout, unit);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();
            throw (E) e.getCause();
        }
    }

    private V runSync() throws InterruptedException, E {
        try {
            return requestSync();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException)
                throw (RuntimeException) e.getCause();
            throw (E) e.getCause();
        }
    }

    /**
     * Called when the fiber calling {@link #run()} is interrupted during the call.
     */
    protected void interrupted() {}

    /**
     * A user of this class must override this method to start the asynchronous operation and register the callback.
     * This method may not use any {@link ThreadLocal}s.
     */
    protected abstract void requestAsync();

    /**
     * Called if {@link #run()} is not being executed in a fiber. Should perform the operation synchronously and return its result.
     * The default implementation of this method throws an `IllegalThreadStateException`.
     *
     * @return The operation's result.
     * @throws E
     * @throws InterruptedException
     */
    protected V requestSync() throws E, InterruptedException, ExecutionException {
        throw new IllegalThreadStateException("Method called not from within a fiber");
    }

    /**
     * Called if {@link #run(long, TimeUnit)} is not being executed in a fiber. Should perform the operation synchronously and return its result.
     * The default implementation of this method throws an `IllegalThreadStateException`.
     *
     * @return The operation's result.
     * @param timeout the maximum duration to wait for the result
     * @param unit    {@code timeout}'s time unit
     * @throws E
     * @throws InterruptedException
     */
    protected V requestSync(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException, E {
        throw new IllegalThreadStateException("Method called not from within a fiber");
    }

    /**
     * This method must be called by the callback upon successful completion of the asynchronous operation.
     *
     * @param result The operation's result
     */
    protected void asyncCompleted(V result) {
        if (completed) // probably timeout
            return;
        this.result = result;
        completed = true;
        Fiber.unpark(fiber);
    }

    /**
     * This method must be called by the callback upon a failure of the asynchronous operation.
     *
     * @param t The exception that caused the failure, or an exception to be associated with it. Must not be `null`.
     */
    protected void asyncFailed(Throwable t) {
        if (t == null)
            throw new IllegalArgumentException("t must not be null");
        if (completed) // probably timeout
            return;
        this.exception = t;
        completed = true;
        Fiber.unpark(fiber);
    }

    /**
     * Tests whether or not the asynchronous operation represented by this `FiberAsyc` has completed.
     */
    public final boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the result of the asynchronous operation if it has completed, or throws an exception if it completed unsuccessfully.
     * If the operation has not yet completed, this method throws an `IllegalStateException`.
     *
     * @return the result of the asynchronous operation if it has completed.
     * @throws E                     if the async computation failed and an exception was set in a call to {@link #asyncFailed(java.lang.Throwable) asyncFailed}.
     * @throws IllegalStateException if the operation has not yet completed.
     */
    public final V getResult() throws E {
        if (!completed)
            throw new IllegalStateException("Not completed");
        if (exception != null)
            throw wrapException(exception);
        return result;
    }

    /**
     * Takes the exception generated by the async operation and possibly wraps it in an exception
     * that will be thrown by the {@code run} method.
     */
    protected E wrapException(Throwable t) {
        return (E) t;
    }

    /**
     * Runs a thread-blocking operation on a given thread pool, blocks (the fiber) until the operation completes and returns
     * its result.
     * This method is useful to transform thread-blocking calls that don't have corresponding asynchronous operations
     * into a fiber-blocking operation.
     *
     * @param exec     the thread-pool on which the thread-blocking operation will be run
     * @param callable the operation
     * @return the result of the operation
     * @throws E if the operation has thrown an exception
     */
    public static <V, E extends Exception> V runBlocking(final ExecutorService exec, final CheckedCallable<V, E> callable) throws E, InterruptedException {
        return new ThreadBlockingFiberAsync<>(exec, callable).run();
    }

    /**
     * Runs a thread-blocking operation on a given thread pool, blocks (the fiber) until the operation completes
     * (but no longer than the specified timeout) and returns its result.
     * This method is useful to transform thread-blocking calls that don't have corresponding asynchronous operations
     * into a fiber-blocking operation.
     *
     * @param exec     the thread-pool on which the thread-blocking operation will be run
     * @param timeout  the maximum duration to wait for the operation to complete
     * @param unit     {@code timeout}'s time unit.
     * @param callable the operation
     * @return the result of the operation
     * @throws E                if the operation has thrown an exception
     * @throws TimeoutException if the timeout expires before the operation completes.
     */
    public static <V, E extends Exception> V runBlocking(final ExecutorService exec, long timeout, TimeUnit unit, final CheckedCallable<V, E> callable) throws E, InterruptedException, TimeoutException {
        return new ThreadBlockingFiberAsync<>(exec, callable).run(timeout, unit);
    }

    /**
     * Runs a thread-blocking operation on a given thread pool, blocks (the fiber) until the operation completes
     * (but no longer than the specified timeout) and returns its result.
     * This method is useful to transform thread-blocking calls that don't have corresponding asynchronous operations
     * into a fiber-blocking operation.
     *
     * @param exec     the thread-pool on which the thread-blocking operation will be run
     * @param timeout  the maximum duration to wait for the operation to complete
     * @param callable the operation
     * @return the result of the operation
     * @throws E                if the operation has thrown an exception
     * @throws TimeoutException if the timeout expires before the operation completes.
     */
    public static <V, E extends Exception> V runBlocking(final ExecutorService exec, Timeout timeout, final CheckedCallable<V, E> callable) throws E, InterruptedException, TimeoutException {
        return new ThreadBlockingFiberAsync<>(exec, callable).run(timeout);
    }

    private static class ThreadBlockingFiberAsync<V, E extends Exception> extends FiberAsync<V, E> {
        private final ExecutorService exec;
        private final CheckedCallable<V, E> action;
        private Future<?> fut;

        public ThreadBlockingFiberAsync(ExecutorService exec, CheckedCallable<V, E> action) {
            this.exec = exec;
            this.action = action;
        }

        @Override
        protected void requestAsync() {
            this.fut = exec.submit(() -> {
                try {
                    V res = action.call();
                    asyncCompleted(res);
                } catch (Throwable e) {
                    asyncFailed(e);
                }
            });
        }

        @Override
        protected V requestSync() throws E {
            return action.call();
        }

        @Override
        protected void interrupted() {
            if (fut != null)
                fut.cancel(true);
        }
    }
}