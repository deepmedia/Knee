package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Test

// TODO: test garbage collection
class ClassTests {

    companion object {
        init {
            System.loadLibrary("test_classes")
        }
    }

    @Test
    fun testProperty() {
        val item = Counter()
        currentCounter = item
        check(item == currentCounter)
        check(item !== currentCounter)
    }

    @Test
    fun testEquals() {
        // Counter implements equals based on the value
        val first = Counter(initialValue = 50u)
        val second = Counter(initialValue = 100u)
        val third = Counter(initialValue = 100u)
        check(first != second)
        check(second == third)
    }

    @Test
    fun testString() {
        val item = Counter(initialValue = 0u)
        check(item.toString() == "Counter(0)")
        item.increment()
        check(item.toString() == "Counter(1)")
    }

    @Test
    fun testHashCode() {
        val item = Counter(initialValue = 30u)
        check(30 == item.hashCode())
    }

    @Test
    fun testSameNativeObject() {
        val item = Counter()
        currentCounter = item
        check(isCurrentCounter(item, checkIdentity = false))
        check(isCurrentCounter(item, checkIdentity = true))
    }

    @Test
    fun testArray() {
        val array = arrayOfCounters(4)
        check(array.size == 4)
    }

    @Test
    fun testMutability() {
        currentCounter = Counter(10u)
        currentCounter!!.increment()
        check(currentCounter!!.get() == 11u)
    }

    @Test
    fun testFlip() {
        val item = Counter()
        check(item.flip(false))
    }
}
