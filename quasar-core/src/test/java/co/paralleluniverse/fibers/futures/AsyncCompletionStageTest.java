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
package co.paralleluniverse.fibers.futures;

import co.paralleluniverse.common.util.Exceptions;

import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.After;

/**
 *
 * @author pron
 */
public class AsyncCompletionStageTest {
    private ExecutorService scheduler;

    public AsyncCompletionStageTest() {
        scheduler = Executors.newWorkStealingPool();
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Test
    public void simpleTest1() throws Exception {
        final CompletableFuture<String> fut = new CompletableFuture<>();

        final co.paralleluniverse.fibers.Fiber<String> fiber = new co.paralleluniverse.fibers.Fiber<>(scheduler, () -> {
            try {
                return fut.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(200);
                fut.complete("hi!");
            } catch (InterruptedException ignored) {}
        }).start();

        assertThat(fiber.get(), equalTo("hi!"));

    }

    @Test
    public void testException() throws Exception {
        final CompletableFuture<String> fut = new CompletableFuture<>();

        final co.paralleluniverse.fibers.Fiber<String> fiber = new co.paralleluniverse.fibers.Fiber<>(scheduler, () -> {
            try {
                final String res = fut.get();
                fail();
                return res;
            } catch (ExecutionException e) {
                throw Exceptions.rethrow(e.getCause());
            }
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(200);
                fut.completeExceptionally(new RuntimeException("haha!"));
            } catch (InterruptedException ignored) {}
        }).start();

        try {
            fiber.get();
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause().getMessage(), equalTo("haha!"));
        }
    }
}
