package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class PrimitiveCollectionsTests {

    companion object {
        init {
            System.loadLibrary("test_primitives")
        }
    }

    @Test
    fun testIntArrays() {
        val a = intArrayOf(1, 2)
        val b = intArrayOf(3, 4)
        check((a + b).contentEquals(sumIntArrays(a, b)))
    }

    @Test
    fun testIntLists() {
        val a = listOf(1, 2)
        val b = listOf(3, 4)
        check((a + b) == sumIntLists(a, b))
    }

    @Test
    fun testLongArrays() {
        val a = longArrayOf(1, 2)
        val b = longArrayOf(3, 4)
        check((a + b).contentEquals(sumLongArrays(a, b)))
    }

    @Test
    fun testLongLists() {
        val a = listOf(1L, 2L)
        val b = listOf(3L, 4L)
        check((a + b) == sumLongLists(a, b))
    }

    @Test
    fun testByteArrays() {
        val a = byteArrayOf(1, 2)
        val b = byteArrayOf(3, 4)
        check((a + b).contentEquals(sumByteArrays(a, b)))
    }

    @Test
    fun testByteLists() {
        val a = listOf(1.toByte(), 2.toByte())
        val b = listOf(3.toByte(), 4.toByte())
        check((a + b) == sumByteLists(a, b))
    }

    @Test
    fun testFloatArrays() {
        val a = floatArrayOf(1F, 2F)
        val b = floatArrayOf(3F, 4F)
        check((a + b).contentEquals(sumFloatArrays(a, b)))
    }

    @Test
    fun testFloatLists() {
        val a = listOf(1F, 2F)
        val b = listOf(3F, 4F)
        check((a + b) == sumFloatLists(a, b))
    }

    @Test
    fun testDoubleArrays() {
        val a = doubleArrayOf(1.0, 2.0)
        val b = doubleArrayOf(3.0, 4.0)
        check((a + b).contentEquals(sumDoubleArrays(a, b)))
    }

    @Test
    fun testDoubleLists() {
        val a = listOf(1.0, 2.0)
        val b = listOf(3.0, 4.0)
        check((a + b) == sumDoubleLists(a, b))
    }

    @Test
    fun testBooleanArrays() {
        val a = booleanArrayOf(false, true)
        val b = booleanArrayOf(true, false)
        check((a + b).contentEquals(sumBooleanArrays(a, b)))
    }

    @Test
    fun testBoleanLists() {
        val a = listOf(false, true)
        val b = listOf(true, false)
        check((a + b) == sumBooleanLists(a, b))
    }

    @Test
    fun testStringArrays() {
        val a = arrayOf("a0", "a1")
        val b = arrayOf("b0", "b1")
        check((a + b).contentEquals(sumStringArrays(a, b)))
    }

    @Test
    fun testStringLists() {
        val a = listOf("a0", "a1")
        val b = listOf("b0", "b1")
        check((a + b) == sumStringLists(a, b))
    }

}
