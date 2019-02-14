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
import kotlin.reflect.KProperty
import kotlin.test.assertFalse


/**
 * @author circlespainter
 */

class OOTest {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun fiberSleepInline() {
            co.paralleluniverse.fibers.Fiber.sleep(1)
        }
    }

    val scheduler = DefaultFiberScheduler.getInstance()

    class D {
        var v = true

        operator fun getValue(thisRef: Any?, prop: KProperty<*>): Boolean {
            fiberSleepInline()
            return v
        }

        @Suppress("UNUSED_PARAMETER")
        operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Boolean) {
            fiberSleepInline()
            v = value
        }
    }

    @Test
    fun testLocalValDelegProp() {
        val ip by D()
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ip
        }).start().get())
    }

    @Test
    fun testLocalVarGetDelegProp() {
        @Suppress("CanBeVal")
        var mp by D()
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mp
        }).start().get())
    }

    @Test
    fun testLocalVarSetDelegProp() {
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var mp by D()
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mp = false
            mp
        }).start().get())
    }

    @Test
    fun testLocalValInlineDelegProp() {
        val ipInline by DInline()
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ipInline
        }).start().get())
    }

    @Test
    fun testLocalVarInlineGetDelegProp() {
        @Suppress("CanBeVal")
        var mpInline by DInline()
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mpInline
        }).start().get())
    }

    @Test
    fun testLocalVarInlineSetDelegProp() {
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var mpInline by DInline()
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mpInline = false
            mpInline
        }).start().get())
    }

    val iv = true
        get() {
            fiberSleepInline()
            return field
        }

    @Test
    fun testOOValPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            iv
        }).start().get())
    }

    @Test
    fun testOOValPropRefGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::iv.get(this)
        }).start().get())
    }

    var mv = true
        get() {
            fiberSleepInline()
            return field
        }
        set(v) {
            fiberSleepInline()
            field = v
        }

    @Test
    fun testOOVarPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mv
        }).start().get())
    }

    @Test
    fun testOOVarPropSet() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mv = false
            mv
        }).start().get())
    }

    @Test
    fun testOOVarPropRefGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mv.get(this)
        }).start().get())
    }

    @Test
    fun testOOVarPropRefSet() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mv.set(this, false)
            OOTest::mv.get(this)
        }).start().get())
    }

    var md by D()
        get
        set

    @Test
    fun testOODelegVarPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            md
        }).start().get())
    }

    @Test
    fun testOODelegVarPropSet() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            md = false
            md
        }).start().get())
    }

    @Test
    fun testOODelegVarPropRefGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::md.get(this)
        }).start().get())
    }

    @Test
    fun testOODelegVarPropRefSet() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::md.set(this, false)
            OOTest::md.get(this)
        }).start().get())
    }

    val ivInline: Boolean
        inline get() {
            fiberSleepInline()
            return true
        }

    @Test
    fun testOOValPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            ivInline
        }).start().get())
    }

    @Test
    fun testOOValPropRefGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::ivInline.get(this)
        }).start().get())
    }

    var mvInline: Boolean
        inline get() {
            fiberSleepInline()
            return true
        }
        inline set(_) {
            fiberSleepInline()
        }

    @Test
    fun testOOVarPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvInline
        }).start().get())
    }

    @Test
    fun testOOVarPropSetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mvInline = false
            mvInline
        }).start().get())
    }

    @Test
    fun testOOVarPropRefGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mvInline.get(this)
        }).start().get())
    }

    @Test
    fun testOOVarPropRefSetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mvInline.set(this, false)
            OOTest::mvInline.get(this)
        }).start().get())
    }

    class DInline {
        var v = true

        @Suppress("NOTHING_TO_INLINE")
        inline operator fun getValue(thisRef: Any?, prop: KProperty<*>): Boolean {
            fiberSleepInline()
            return v
        }

        @Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
        inline operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: Boolean) {
            fiberSleepInline()
            v = value
        }
    }

    var mdInline by DInline()
        get
        set

    @Test
    fun testOODelegVarPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mdInline
        }).start().get())
    }

    @Test
    fun testOODelegVarPropSetInline() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            mdInline = false
            mdInline
        }).start().get())
    }

    @Test
    fun testOODelegVarPropRefGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mdInline.get(this)
        }).start().get())
    }

    @Test
    fun testOODelegVarPropRefSetInline() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest::mdInline.set(this, false)
            OOTest::mdInline.get(this)
        }).start().get())
    }

    enum class E(val data: Int?) {
        V1(0),
        V2(1) {
            override fun enumFun() {
                fiberSleepInline()
            }
        };

        open fun enumFun() {
            data
            fiberSleepInline()
        }
    }

    @Test
    fun testOOEnum1() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.enumFun()
            true
        }).start().get())
    }

    @Test
    fun testOOEnum2() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.enumFun()
            true
        }).start().get())
    }

    @Test
    fun testOOEnumExt() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.doFiberSleep()
            true
        }).start().get())
    }

    @Suppress("unused")
    val E.ivE: Boolean
        get() {
            fiberSleepInline()
            return true
        }

    @Test
    fun testOOExtValPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.ivE
        }).start().get())
    }

    @Suppress("unused")
    var E.mvE: Boolean
        get() {
            fiberSleepInline()
            return true
        }
        set(_) {
            fiberSleepInline()
        }

    @Test
    fun testOOExtVarPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.mvE
        }).start().get())
    }

    @Test
    fun testOOExtVarPropSet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.mvE = false
            E.V2.mvE
        }).start().get())
    }

    @Suppress("unused")
    var E.mdE by D()
        get
        set

    @Test
    fun testOOExtDelegVarPropGet() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.mdE
        }).start().get())
    }

    @Test
    fun testOOExtDelegVarPropSet() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.mdE = false
            E.V1.mdE
        }).start().get())
    }

    @Suppress("unused")
    val E.ivEInline: Boolean
        inline get() {
            fiberSleepInline()
            return true
        }

    @Test
    fun testOOExtValPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.ivEInline
        }).start().get())
    }

    @Suppress("unused")
    var E.mvEInline: Boolean
        inline get() {
            fiberSleepInline()
            return true
        }
        inline set(_) {
            fiberSleepInline()
        }

    @Test
    fun testOOExtVarPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.mvEInline
        }).start().get())
    }

    @Test
    fun testOOExtVarPropSetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V2.mvEInline = false
            E.V2.mvEInline
        }).start().get())
    }

    @Suppress("unused")
    var E.mdEInline by DInline()
        get
        set

    @Test
    fun testOOExtDelegVarPropGetInline() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.mdEInline
        }).start().get())
    }

    @Test
    fun testOOExtDelegVarPropSetInline() {
        assertFalse(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            E.V1.mdEInline = false
            E.V1.mdEInline
        }).start().get())
    }

    fun outerDoSleep(): Boolean {
        fiberSleepInline()
        return true
    }

    open class Base(val data: Int = 0) {
        // NOT SUPPORTED: Kotlin's initializers are named <init> and we don't instrument those. Not an issue
        // because they're called by constructors which we don't instrument either (because it is most probably
        // impossible to unpark them).

        // { fiberSleepInline() }

        open fun doSleep(): Boolean {
            data
            fiberSleepInline()
            return true
        }
    }

    @Test
    fun testOOMethodRef() {
        val b = Base()
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable { b.doSleep() }).start().get())

    }

    interface BaseTrait1 {
        fun doSleep(): Boolean {
            fiberSleepInline()
            return true
        }
    }

    interface BaseTrait2 {
        fun doSleep(): Boolean {
            fiberSleepInline()
            return true
        }
    }

    open class Derived(data: Int) : Base(data) {
        final override fun doSleep(): Boolean {
            fiberSleepInline()
            return true
        }
    }

    abstract class DerivedAbstract1 : Base(1) {
        override abstract fun doSleep(): Boolean
    }

    class DerivedDerived1 : Base(1) {
        override fun doSleep(): Boolean {
            fiberSleepInline()
            return true
        }
    }

    abstract class DerivedDerived2 : BaseTrait1, DerivedAbstract1()

    class DerivedDerived3 : DerivedAbstract1(), BaseTrait1, BaseTrait2 {
        override fun doSleep(): Boolean {
            return super<BaseTrait2>.doSleep()
        }
    }

    open inner class InnerDerived : DerivedAbstract1(), BaseTrait2 {
        override fun doSleep(): Boolean {
            return outerDoSleep()
        }
    }

    // TODO: https://youtrack.jetbrains.com/issue/KT-10532, still open in 1.1.3
    /*
    companion object : DerivedDerived2(), BaseTrait1 {
        override fun doSleep() {
            super<DerivedDerived2>.doSleep()
        }
    }

    @Test public fun testOODefObject() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            OOTest.Companion.doSleep()
            true
        }).start().get())
    }
    */

    class Data : DerivedDerived2(), BaseTrait2 {
        override fun doSleep(): Boolean {
            return super<BaseTrait2>.doSleep()
        }

        fun doSomething() {
            fiberSleepInline()
        }
    }

    class Delegating(bb2: BaseTrait2) : Base(), BaseTrait2 by bb2 {
        override fun doSleep(): Boolean {
            fiberSleepInline()
            return true
        }
    }

    @Suppress("unused")
    fun Any?.doFiberSleep() {
        fiberSleepInline()
    }

    @Test
    fun testOOExtFun() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            null.doFiberSleep()
            true
        }).start().get())
    }

    object O : DerivedDerived2()

    @Test
    fun testOOSimple() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            Base().doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOInheritingObjectLiteral() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            (object : BaseTrait1 {}).doSleep()
            true
        }).start().get())
    }

    @Test
    fun testDerived() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            Derived(1).doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOOverridingObjectLiteral() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            (object : DerivedAbstract1() {
                override fun doSleep(): Boolean {
                    fiberSleepInline()
                    return true
                }
            }).doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOODerived1() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            DerivedDerived1().doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOODerived2() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            (object : DerivedDerived2() {}).doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOODerived3() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            DerivedDerived3().doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOInnerDerived() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            InnerDerived().doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOOuter() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            outerDoSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOData() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            Data().doSomething()
            true
        }).start().get())
    }

    @Test
    fun testOODataInherited() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            Data().doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOOObjectDecl() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            O.doSleep()
            true
        }).start().get())
    }

    @Test
    fun testOODeleg() {
        assertTrue(co.paralleluniverse.fibers.Fiber(scheduler, Callable {
            Delegating(DerivedDerived3()).doSleep()
            true
        }).start().get())
    }
}
