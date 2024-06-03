package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class NullablePrimitiveTests {

    companion object {
        init {
            System.loadLibrary("test_primitives")
        }
    }

    @Test
    fun testNullableInts() {
        check("null" == printNullableInt(null))
        check("7" == printNullableInt(7))
    }

    @Test
    fun testNullableString() {
        check("null" == printNullableString(null))
        check("hey" == printNullableString("hey"))
    }

    @Test
    fun testNullableBoolean() {
        check("null" == printNullableBoolean(null))
        check("true" == printNullableBoolean(true))
    }

    @Test
    fun testNullableByteArray() {
        check("null" == printNullableByteArray(null))
        check("[0, 1, 2]" == printNullableByteArray(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun testNullableBooleanList() {
        check("null" == printNullableBooleanList(null))
        check("[false, true]" == printNullableBooleanList(listOf(false, true)))
    }

    @Test
    fun testNullableListSize() {
        check(null == getNullableListSize(null))
        check(4 == getNullableListSize(listOf(0, 1, 2, 3)))
    }

    @Test
    fun testCreateNullableList() {
        check(null == createNullableList(null, null, null))
        check(listOf(1) == createNullableList(null, 1, null))
    }

}
