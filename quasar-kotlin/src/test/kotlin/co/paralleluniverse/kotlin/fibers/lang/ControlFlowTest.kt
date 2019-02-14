/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2015-2017, Parallel Universe Software Co. All rights reserved.
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
package co.paralleluniverse.kotlin.fibers.lang

import co.paralleluniverse.fibers.DefaultFiberScheduler
import co.paralleluniverse.strands.channels.Channels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 *
 * @author circlespainter
 */
class ControlFlowTest {
    private val scheduler = DefaultFiberScheduler.getInstance()

    @Test
    fun testForAndWhile() {
        val ch = Channels.newIntChannel(0)
        val vals = listOf(0, 1)
        val fiberSend = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            for (v in vals)
                ch.send(v)
            ch.close()
        }).start()
        val fiberReceive = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            var l = listOf<Int>()
            var i = ch.receive()
            while (i != null) {
                l = l.plus(i)
                i = ch.receive()
            }
            assertEquals(vals, l)
        }).start()
        fiberSend.join()
        fiberReceive.join()
    }

    @Test
    fun testWhen() {
        val ch = Channels.newIntChannel(0)
        val vals = listOf(1, 101)
        val fiberSend = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            for (v in vals)
                ch.send(v)
            ch.close()
        }).start()
        val fiberReceive = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            var l = listOf<Boolean>()
            var i = ch.receive()
            while (i != null) {
                when (i) {
                    in 1..100 -> {
                        l = l.plus(true)
                        i = ch.receive()
                    }
                    else -> {
                        l = l.plus(false)
                        i = ch.receive()
                    }
                }
            }
            assertEquals(listOf(true, false), l)
        }).start()
        fiberSend.join()
        fiberReceive.join()
    }

    @Test
    fun testHOFun() {
        val ch = Channels.newIntChannel(0)
        val vals = listOf(0, 1)
        val fiberSend = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            for (v in vals)
                ch.send(v)
            ch.close()
        }).start()
        val fiberReceive = co.paralleluniverse.fibers.Fiber<Unit>(scheduler, Callable {
            var l = listOf<Int>()
            var i = ch.receive()
            while (i != null) {
                l = l.plus(i)
                i = ch.receive()
            }
            fun f() {
                l.forEach { x ->
                    co.paralleluniverse.fibers.Fiber.park(10, TimeUnit.MILLISECONDS)
                    if (x % 2 != 0)
                        return@f
                }
            }
            f()
            assertEquals(vals, l)
        }).start()
        fiberSend.join()
        fiberReceive.join()
    }
}