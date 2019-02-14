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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable

/**
 * @author circlespainter
 */

fun seq(f: () -> Unit, g: () -> Unit): () -> Unit {
    println("seq")
    return ({ f(); g() })
}

fun f() {
    co.paralleluniverse.fibers.Fiber.sleep(10)
    fun f1() {
        co.paralleluniverse.fibers.Fiber.sleep(10)
    }
    f1()
}

fun fDef(@Suppress("UNUSED_PARAMETER") def: Boolean = true) = co.paralleluniverse.fibers.Fiber.sleep(10)

fun fQuick() {
    println("quick pre-sleep")
    co.paralleluniverse.fibers.Fiber.sleep(10)
    println("quick after-sleep")
}

fun fVarArg(vararg ls: Long) {
    for (l in ls)
        co.paralleluniverse.fibers.Fiber.sleep(l)
}

class FunTest {
    val scheduler = DefaultFiberScheduler.getInstance()

    @Test
    fun testSimpleFun() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            f()
            true
        }).start().get())
    }

    @Test
    fun testDefaultFunWithAllArgs() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            fDef(true)
            true
        }).start().get())
    }

    @Test
    fun testDefaultFunWithoutSomeArgs() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            fDef()
            true
        }).start().get())
    }

    @Test
    fun testQuickFun() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            fQuick()
            true
        }).start().get())
    }

    @Test
    fun testVarArgFun0() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            fVarArg()
            true
        }).start().get())
    }

    @Test
    fun testVarArgFun1() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            fVarArg(10)
            true
        }).start().get())
    }

    @Test
    fun testFunRefInvoke() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            (::fQuick)()
            true
        }).start().get())
    }

    @Test
    fun testFunRefArg() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            seq(::fQuick, ::fQuick)()
            true
        }).start().get())
    }

    @Test
    fun testFunLambda() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ({ _: Int -> co.paralleluniverse.fibers.Fiber.sleep(10) })(1)
            true
        }).start().get())
    }


    private fun callSusLambda(f: (Int) -> Unit, i: Int) =
        co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            f(i)
            true
        }).start().get()

    @Test
    fun testFunLambda2() = assertTrue(callSusLambda({ co.paralleluniverse.fibers.Fiber.sleep(10) }, 1))

    @Test
    fun testFunAnon() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            (fun(_: Int) { co.paralleluniverse.fibers.Fiber.sleep(10) })(1)
            true
        }).start().get())
    }

    @Test
    fun testFunAnon2() = assertTrue(callSusLambda(fun(_: Int) { co.paralleluniverse.fibers.Fiber.sleep(10) }, 1))
}
