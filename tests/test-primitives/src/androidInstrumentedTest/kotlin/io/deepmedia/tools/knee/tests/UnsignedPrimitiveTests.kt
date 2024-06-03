package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class UnsignedPrimitiveTests {

    companion object {
        init {
            System.loadLibrary("test_primitives")
        }
    }

    @Test
    fun testUInts() {
        check(10u == sumUInts(3u, 7u))
    }

    @Test
    fun testULongs() {
        check(10000000000UL == sumULongs(3000000000UL, 7000000000UL))
    }

    @Test
    fun testUBytes() {
        check(10.toUByte() == sumUBytes(3u, 7u))
    }
}
