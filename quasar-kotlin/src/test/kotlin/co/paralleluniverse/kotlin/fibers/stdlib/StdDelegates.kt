/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2017, Parallel Universe Software Co. All rights reserved.
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
package co.paralleluniverse.kotlin.fibers.stdlib

import co.paralleluniverse.fibers.DefaultFiberScheduler
import co.paralleluniverse.kotlin.quasarLazy
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Callable
import kotlin.properties.Delegates
import kotlin.test.assertNull

/**
 * @author circlespainter
 */
class StdDelegates {
    val scheduler = DefaultFiberScheduler.getInstance()

    @Test
    fun testLocalValLazySyncDelegProp() {
        val ipLazySync by quasarLazy {
            co.paralleluniverse.fibers.Fiber.sleep(1)
            true
        }
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazySync
        }).start().get())
    }

    private val ipLazySync by quasarLazy {
        co.paralleluniverse.fibers.Fiber.sleep(1)
        true
    }

    @Test
    fun testValLazySyncDelegProp() {
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazySync
        }).start().get())
    }

    @Test
    fun testLocalValLazyPubDelegProp() {
        val ipLazyPub by lazy(LazyThreadSafetyMode.PUBLICATION) {
            co.paralleluniverse.fibers.Fiber.sleep(1)
            true
        }
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazyPub
        }).start().get())
    }

    private val ipLazyPub by lazy(LazyThreadSafetyMode.PUBLICATION) {
        co.paralleluniverse.fibers.Fiber.sleep(1)
        true
    }

    @Test
    fun testValLazyPubDelegProp() {
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazyPub
        }).start().get())
    }

    @Test
    fun testLocalValLazyUnsafeDelegProp() {
        val ipLazyNone by lazy(LazyThreadSafetyMode.NONE) {
            co.paralleluniverse.fibers.Fiber.sleep(1)
            true
        }
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazyNone
        }).start().get())
    }

    private val ipLazyNone by lazy(LazyThreadSafetyMode.NONE) {
        co.paralleluniverse.fibers.Fiber.sleep(1)
        true
    }

    @Test
    fun testValLazyUnsafeDelegProp() {
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipLazyNone
        }).start().get())
    }

    @Test
    fun testLocalValObservableDelegProp() {
        val ivObs: Boolean? by Delegates.observable(true) { _, old, new ->
            co.paralleluniverse.fibers.Fiber.sleep(1)
            println("$old -> $new")
        }
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ivObs
        }).start().get()!!)
    }

    val ivObs: Boolean? by Delegates.observable(true) { _, old, new ->
        co.paralleluniverse.fibers.Fiber.sleep(1)
        println("$old -> $new")
    }

    @Test
    fun testValObservableDelegProp() {
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ivObs
        }).start().get()!!)
    }

    @Test
    fun testLocalVarObservableGetDelegProp() {
        @Suppress("CanBeVal")
        var mvObs: Boolean? by Delegates.observable<Boolean?>(null) { _, old, new ->
            co.paralleluniverse.fibers.Fiber.sleep(1)
            println("$old -> $new")
        }
        assertNull(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvObs
        }).start().get())
    }

    @Test
    fun testLocalVarObservableSetDelegProp() {
        var mvObs: Boolean? by Delegates.observable<Boolean?>(null) { _, old, new ->
            co.paralleluniverse.fibers.Fiber.sleep(1)
            println("$old -> $new")
        }
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvObs = true
            mvObs
        }).start().get()!!)
    }

    var mvObs: Boolean? by Delegates.observable<Boolean?>(null) { _, old, new ->
        co.paralleluniverse.fibers.Fiber.sleep(1)
        println("$old -> $new")
    }

    @Test
    fun testVarObservableGetDelegProp() {
        assertNull(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvObs
        }).start().get())
    }

    @Test
    fun testVarObservableSetDelegProp() {
        Assert.assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvObs = true
            mvObs
        }).start().get()!!)
    }
}
