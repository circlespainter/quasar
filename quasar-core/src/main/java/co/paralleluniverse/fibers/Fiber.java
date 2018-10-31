/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2017, Parallel Universe Software Co. All rights reserved.
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

import co.paralleluniverse.common.monitoring.FlightRecorder;
import co.paralleluniverse.common.monitoring.FlightRecorderMessage;
import co.paralleluniverse.common.util.Debug;
import co.paralleluniverse.strands.RunnableCallableUtils;
import co.paralleluniverse.strands.Strand;
import co.paralleluniverse.strands.Stranded;
import co.paralleluniverse.strands.dataflow.Val;

import java.io.Serializable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * A lightweight thread.
 * <p/>
 * There are two ways to create a new fiber: either subclass the {@code Fiber} class and override the {@code run} method,
 * or pass the code to be executed in the fiber as the {@code target} parameter to the constructor. All in all, the Fiber API
 * resembles the {@link Thread} class in many ways.
 * <p/>
 * A fiber runs inside a ForkJoinPool.
 * <p/>
 * A Fiber can be serialized if it's not running and all involved classes and data types are also {@link Serializable}.
 * <p/>
 * A new Fiber occupies under 400 bytes of memory (when using the default stack size, and compressed OOPs are turned on, as they are by default).
 *
 * @param <V> The type of the fiber's result value. Should be set to {@link Void} if no value is to be returned by the fiber.
 *
 * @author pron
 */
final public class Fiber<V> extends Strand implements Joinable<V>, Serializable, Future<V> {
    private static final FlightRecorder flightRecorder = Debug.isDebug() ? Debug.getGlobalFlightRecorder() : null;

    static {
        if (Debug.isDebug())
            System.err.println("QUASAR WARNING: Debug mode enabled. This may harm performance.");
        if (Debug.isAssertionsEnabled())
            System.err.println("QUASAR WARNING: Assertions enabled. This may harm performance.");
    }

    // private static final FiberTimedScheduler timeoutService = new FiberTimedScheduler(new ThreadFactoryBuilder().setNameFormat("fiber-timeout-%d").setDaemon(true).build());
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler = (s, e) -> {
        System.err.print("Exception in Fiber \"" + s.getName() + "\" ");
        System.err.println(e);
        Strand.printStackTrace(e.getStackTrace(), System.err);
    };
    private static final AtomicLong idGen = new AtomicLong(10000000L);
    private static long nextFiberId() {
        return idGen.incrementAndGet();
    }

    private volatile boolean interrupted;

    private String name;
    private /*final*/ transient long fid;
    private Callable<V> target;
    private Executor scheduler;
    private java.lang.Fiber fiber;
    private java.lang.Thread fiberThread;

    private transient Val<V> result; // transient b/c completed fibers are not serialized
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * Creates a new fiber from the given {@link Callable}.
     *
     * @param name      The name of the fiber (may be {@code null})
     * @param target    the {@link Callable} for the fiber.
     */
    @SuppressWarnings("LeakingThisInConstructor")
    public Fiber(String name, Executor scheduler, Callable<V> target) {
        this.scheduler = scheduler != null ? scheduler : DefaultFiberScheduler.getInstance();
        this.fid = nextFiberId();
        this.target = target;
        this.result = new Val<>();

        setName(name);

        final Strand parent = Strand.currentStrand(); // retaining the parent as a field is a huge, complex memory leak
        if (Debug.isDebug()) {
            record(1, "Fiber", "<init>", "Creating fiber name: %s, scheduler: %s, parent: %s, target: %s", name, scheduler, parent, target);
        }

        if (target != null) {
            if (target instanceof Stranded) {
                ((Stranded) target).setStrand(this);
            }
        }

        record(1, "Fiber", "<init>", "Created fiber %s", this);
    }

    public Fiber(java.lang.Fiber f) {
        this((String) null, (Callable) null);
        this.fiber = f;
    }

    private Future<V> future() {
        return result;
    }

    @Override
    public String toString() {
        return fiber.toString();
    }

    @Override
    public int hashCode() {
        return fiber.hashCode();
    }

    @Override
    public final String getName() {
        if (name == null) // benign race
            this.name = "fiber-" + (scheduler.toString() + '-') + fid;
        return name;
    }

    @Override
    public final Fiber<V> setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public long getId() {
        return fid;
    }

    //<editor-fold defaultstate="collapsed" desc="Constructors">
    /////////// Constructors ///////////////////////////////////

    /**
     * Creates a new Fiber from the given {@link Callable}.
     * The new fiber has no name, and uses the default initial stack size.
     *
     * @param scheduler The scheduler pool in which the fiber should run.
     * @param target    the Runnable for the Fiber.
     * @throws NullPointerException     when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(Executor scheduler, Callable<V> target) {
        this((String) null, scheduler, target);
    }

    /**
     * Creates a new Fiber from the given {@link Runnable}.
     *
     * @param name      The name of the fiber (may be null)
     * @param scheduler The scheduler pool in which the fiber should run.
     * @param target    the Runnable for the Fiber.
     * @throws NullPointerException     when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(String name, Executor scheduler, Runnable target) {
        this(name, scheduler, (Callable<V>) RunnableCallableUtils.runnableToCallable(target));
    }
    /**
     * Creates a new Fiber from the given Runnable.
     * The new fiber has no name, and uses the default initial stack size.
     *
     * @param scheduler The scheduler pool in which the fiber should run.
     * @param target    the Runnable for the Fiber.
     * @throws NullPointerException     when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(Executor scheduler, Runnable target) {
        this((String) null, scheduler, target);
    }

    /**
     * Creates a new child Fiber from the given {@link Callable}.
     * This constructor may only be called from within another fiber. This fiber will use the same fork/join pool as the creating fiber.
     * The new fiber uses the default initial stack size.
     *
     * @param name   The name of the fiber (may be null)
     * @param target the SuspendableRunnable for the Fiber.
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(String name, Callable<V> target) {
        this(name, null, target);
    }

    /**
     * Creates a new child Fiber from the given {@link Callable}.
     * This constructor may only be called from within another fiber. This fiber will use the same fork/join pool as the creating fiber.
     * The new fiber has no name, and uses the default initial stack size.
     *
     * @param target the SuspendableRunnable for the Fiber.
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(Callable<V> target) {
        this((String) null, target);
    }

    /**
     * Creates a new child Fiber from the given {@link Runnable}.
     * This constructor may only be called from within another fiber. This fiber will use the same fork/join pool as the creating fiber.
     *
     * @param name      The name of the fiber (may be null)
     * @param target    the SuspendableRunnable for the Fiber.
     * @throws NullPointerException     when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(String name, Runnable target) {
        this(name, (Callable<V>) RunnableCallableUtils.runnableToCallable(target));
    }

    /**
     * Creates a new child Fiber from the given {@link Runnable}.
     * This constructor may only be called from within another fiber. This fiber will use the same fork/join pool as the creating fiber.
     * The new fiber has no name, and uses the default initial stack size.
     *
     * @param target the SuspendableRunnable for the Fiber.
     * @throws NullPointerException     when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Fiber(Runnable target) {
        this((String) null, target);
    }

    public Fiber(Fiber fiber, Callable<V> target) {
        this(fiber.name, fiber.scheduler, target);
    }

    public Fiber(Fiber fiber, Runnable target) {
        this(fiber.name, fiber.scheduler, target);
    }

    public Fiber(Fiber fiber, Executor scheduler, Callable<V> target) {
        this(fiber.name, scheduler, target);
    }

    public Fiber(Fiber fiber, Executor scheduler, Runnable target) {
        this(fiber.name, scheduler, target);
    }
    //</editor-fold>

    /**
     * Returns the active Fiber on this thread or NULL if no Fiber is running.
     *
     * @return the active Fiber on this thread or NULL if no Fiber is running.
     */
    public static Fiber currentFiber() {
        return getCurrentFiber();
    }

    /**
     * Tests whether current code is executing in a fiber.
     * This method <i>might</i> be faster than {@code Fiber.currentFiber() != null}.
     *
     * @return {@code true} if called in a fiber; {@code false} otherwise.
     */
    public static boolean isCurrentFiber() {
        return java.lang.Strand.currentStrand() instanceof java.lang.Fiber;
    }

    @Override
    public final boolean isFiber() {
        return true;
    }

    @Override
    public final Object getUnderlying() {
        return this;
    }


    public static boolean interrupted() {
        final Fiber current = currentFiber();
        if (current == null)
            throw new IllegalStateException("Not called on a fiber");
        return current.isInterrupted();
    }

    void setResult(V res) {
        this.result.set(res);
    }

    void setException(Throwable t) {
        this.result.setException(t);
    }

    private static Fiber getCurrentFiber() {
        final java.lang.Strand currentStrand = java.lang.Strand.currentStrand();
        if (!(currentStrand instanceof java.lang.Fiber))
            return null;

        return FiberStrand.get((java.lang.Fiber) currentStrand);
    }

    /**
     *
     * @return {@code this}
     */
    @Override
    public final Fiber<V> start() {
        if (fiber != null)
            throw new IllegalThreadStateException("Fiber has already been started or has died");

        if (target == null)
            throw new IllegalThreadStateException("No target Callable has been provided");

        final Runnable task = () -> {
            try {
                fiberThread = Thread.currentThread();
                if (interrupted) {
                    interrupted = false;
                    fiberThread.interrupt();
                }
                setResult(target.call());
            } catch (final Throwable t) {
                setException(t);
                runFiberExceptionThroughHandlers(t);
            }
        };

        fiber = new java.lang.Fiber(scheduler, task);
        FiberStrand.set(fiber, this);

        fiber = fiber.schedule();

        return this;
    }

    private void runFiberExceptionThroughHandlers(Throwable t) {
        if (uncaughtExceptionHandler != null)
            uncaughtExceptionHandler.uncaughtException(this, t);
        else if (defaultUncaughtExceptionHandler != null)
            defaultUncaughtExceptionHandler.uncaughtException(this, t);
    }

    @Override
    public final void interrupt() {
        if (fiberThread != null)
            fiberThread.interrupt();

        interrupted = true;
    }

    @Override
    public final boolean isInterrupted() {
        if (fiberThread != null)
            return fiberThread.isInterrupted() || interrupted;
        else
            return interrupted;
    }

    @Override
    public void unpark() {
        LockSupport.unpark(fiber);
    }

    @Override
    public Object getBlocker() {
        return null;
    }

    @Override
    public final boolean isAlive() {
        return fiber != null && fiber.isAlive();
    }

    @Override
    public final boolean isTerminated() {
        return fiber != null && !fiber.isAlive();
    }

    @Override
    public final void join() throws ExecutionException, InterruptedException {
        get();
    }

    @Override
    public final void join(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        get(timeout, unit);
    }

    @Override
    public final V get() throws ExecutionException, InterruptedException {
        try {
            return future().get();
        } catch (RuntimeExecutionException t) {
            throw new ExecutionException(unrollExecutionExceptions(t.getCause()));
        }
    }

    @Override
    public final V get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        try {
            return future().get(timeout, unit);
        } catch (RuntimeExecutionException t) {
            throw new ExecutionException(unrollExecutionExceptions(t.getCause()));
        }
    }

    private Throwable unrollExecutionExceptions(Throwable t) {
        if (t instanceof ExecutionException && t != t.getCause() && t.getCause() != null)
            return unrollExecutionExceptions(t.getCause());

        return t;
    }

    @Override
    public final boolean isDone() {
        return isTerminated();
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        if (fiber == null)
            return false;

        fiber.cancel();
        interrupted = fiber.isCancelled();
        return interrupted;
    }

    @Override
    public final boolean isCancelled() {
        return future().isCancelled();
    }

    /**
     * Set the handler invoked when this fiber abruptly terminates
     * due to an uncaught exception.
     * <p>
     * A fiber can take full control of how it responds to uncaught
     * exceptions by having its uncaught exception handler explicitly set.
     *
     * @param eh the object to use as this fiber's uncaught exception
     *           handler. If {@code null} then this fiber has no explicit handler.
     * @see #setDefaultUncaughtExceptionHandler
     */
    @Override
    public final void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        this.uncaughtExceptionHandler = eh;
    }

    /**
     * Returns the handler invoked when this fiber abruptly terminates
     * due to an uncaught exception.
     */
    @Override
    public final UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }

    /**
     * Returns the default handler invoked when a fiber abruptly terminates
     * due to an uncaught exception. If the returned value is {@code null},
     * there is no default.
     *
     * @see #setDefaultUncaughtExceptionHandler
     */
    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    /**
     * Set the default handler invoked when a fiber abruptly terminates
     * due to an uncaught exception, and no other handler has been defined
     * for that fiber.
     *
     * @param eh the object to use as the default uncaught exception handler.
     *           If {@code null} then there is no default handler.
     *
     * @see #setUncaughtExceptionHandler
     * @see #getUncaughtExceptionHandler
     */
    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        Fiber.defaultUncaughtExceptionHandler = eh;
    }

    @Override
    public final StackTraceElement[] getStackTrace() {
        return fiberThread != null ? fiberThread.getStackTrace() : null;
    }

    public Executor getScheduler() {
        return scheduler;
    }

    //<editor-fold defaultstate="collapsed" desc="Recording">
    /////////// Recording ///////////////////////////////////
    protected final boolean isRecordingLevel(int level) {
        if (!Debug.isDebug())
            return false;
        final FlightRecorder.ThreadRecorder recorder = flightRecorder != null ? flightRecorder.get() : null;
        if (recorder == null)
            return false;
        return recorder.recordsLevel(level);
    }

    protected final void record(int level, String clazz, String method, String format) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2, arg3);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2, arg3, arg4);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2, arg3, arg4, arg5);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    protected final void record(int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    protected final void record(int level, String clazz, String method, String format, Object... args) {
        if (flightRecorder != null)
            record(flightRecorder.get(), level, clazz, method, format, args);
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, null));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2, arg3}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2, arg3, arg4}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2, arg3, arg4, arg5}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, new Object[]{arg1, arg2, arg3, arg4, arg5, arg6, arg7}));
    }

    private static void record(FlightRecorder.ThreadRecorder recorder, int level, String clazz, String method, String format, Object... args) {
        if (recorder != null)
            recorder.record(level, makeFlightRecorderMessage(recorder, clazz, method, format, args));
    }

    private static FlightRecorderMessage makeFlightRecorderMessage(FlightRecorder.ThreadRecorder recorder, String clazz, String method, String format, Object[] args) {
        return new FlightRecorderMessage(clazz, method, format, args);
        //return ((FlightRecorderMessageFactory) recorder.getAux()).makeFlightRecorderMessage(clazz, method, format, args);
    }
    //</editor-fold>
}
