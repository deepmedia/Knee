package io.deepmedia.tools.knee.tests

import org.junit.Test

class InnerClassTests {

    companion object {
        init {
            System.loadLibrary("test_classes")
        }
    }


    @Test
    fun testProperty() {
        val item = Outer.InnerCounter()
        currentInnerCounter = item
        check(item == currentInnerCounter)
        check(item !== currentInnerCounter)
    }

    @Test
    fun testEquals() {
        // Counter implements equals based on the value
        val first = Outer.InnerCounter(initialValue = 50u)
        val second = Outer.InnerCounter(initialValue = 100u)
        val third = Outer.InnerCounter(initialValue = 100u)
        check(first != second)
        check(second == third)
    }

    @Test
    fun testString() {
        val item = Outer.InnerCounter(initialValue = 0u)
        check(item.toString() == "Outer.InnerCounter(0)")
        item.increment()
        check(item.toString() == "Outer.InnerCounter(1)")
    }

    @Test
    fun testHashCode() {
        val item = Outer.InnerCounter(initialValue = 30u)
        check(30 == item.hashCode())
    }

    @Test
    fun testSameNativeObject() {
        val item = Outer.InnerCounter()
        currentInnerCounter = item
        check(isCurrentInnerCounter(item, checkIdentity = false))
        check(isCurrentInnerCounter(item, checkIdentity = true))
    }

    @Test
    fun testMutability() {
        currentInnerCounter = Outer.InnerCounter(10u)
        currentInnerCounter!!.increment()
        check(currentInnerCounter!!.get() == 11u)
    }
}
