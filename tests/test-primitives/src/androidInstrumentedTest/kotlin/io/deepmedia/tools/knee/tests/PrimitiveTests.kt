package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class PrimitiveTests {

    companion object {
        init {
            System.loadLibrary("test_primitives")
        }
    }

    @Test
    fun testInts() {
        check(10 == sumInts(3, 7))
    }

    @Test
    fun testLongs() {
        check(10000000000L == sumLongs(3000000000L, 7000000000L))
    }

    @Test
    fun testBytes() {
        check(10.toByte() == sumBytes(3, 7))
    }

    @Test
    fun testFloats() {
        check(abs(1F - sumFloats(0.3F, 0.7F)) < 0.001F)
    }

    @Test
    fun testDoubles() {
        check(abs(1.0 - sumDoubles(0.3, 0.7)) < 0.001)
    }

    @Test
    fun testStrings() {
        check("Hello, world!" == sumStrings("Hello, ", "world!"))
    }

    @Test
    fun testBooleans() {
        check(andBooleans(true, true))
        check(!andBooleans(false, true))
        check(!andBooleans(false, false))
        check(orBooleans(true, false))
        check(orBooleans(true, true))
        check(!orBooleans(false, false))
    }

}
