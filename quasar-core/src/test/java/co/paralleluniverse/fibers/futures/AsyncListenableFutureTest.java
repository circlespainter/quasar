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

import co.paralleluniverse.common.test.TestUtil;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 *
 * @author pron
 */
public class AsyncListenableFutureTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;
    
    private ExecutorService scheduler;

    public AsyncListenableFutureTest() {
        scheduler = Executors.newWorkStealingPool();
    }

    @After
    public void tearDown() {
        scheduler.shutdown();
    }

    @Test
    public void simpleTest1() throws Exception {
        final SettableFuture<String> fut = SettableFuture.create();

        final co.paralleluniverse.fibers.Fiber<String> fiber = new co.paralleluniverse.fibers.Fiber<>(scheduler, (Callable<String>) fut::get).start();

        new Thread(() -> {
            try {
                Thread.sleep(200);
                fut.set("hi!");
            } catch (final InterruptedException ignored) {}
        }).start();

        assertThat(fiber.get(), equalTo("hi!"));

    }

    @Test
    public void testException() throws Exception {
        final SettableFuture<String> fut = SettableFuture.create();

        final co.paralleluniverse.fibers.Fiber<String> fiber = new co.paralleluniverse.fibers.Fiber<>(scheduler, () -> {
            final String res = fut.get();
            fail();
            return res;
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(200);
                fut.setException(new RuntimeException("haha!"));
            } catch (InterruptedException ignored) {}
        }).start();

        try {
            fiber.get();
            fail();
        } catch (final ExecutionException e) {
            assertThat(e.getCause().getMessage(), equalTo("haha!"));
        }
    }

    @Test
    public void testException2() throws Exception {
        final ListenableFuture<String> fut = new AbstractFuture<>() {
            {
                setException(new RuntimeException("haha!"));
            }
        };

        final co.paralleluniverse.fibers.Fiber<String> fiber = new co.paralleluniverse.fibers.Fiber<>(scheduler, () -> {
            final String res = fut.get();
            fail();
            return res;
        }).start();

        try {
            fiber.get();
            fail();
        } catch (final ExecutionException e) {
            assertThat(e.getCause().getMessage(), equalTo("haha!"));
        }
    }
}
