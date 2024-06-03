package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.*
import org.junit.Test

class ReverseSuspendTests {

    companion object {
        init {
            System.loadLibrary("test_coroutines")
        }
    }

    val util = object : ReverseUtil {
        override suspend fun sumInts(first: Int, second: Int, delay: Long): Int {
            if (delay > 0) delay(delay)
            return first + second
        }
        override suspend fun sumLists(first: List<Int>, second: List<Int>, delay: Long): List<Int> {
            if (delay > 0) delay(delay)
            return first + second
        }
        override suspend fun sumStrings(first: String, second: String, delay: Long): String {
            if (delay > 0) delay(delay)
            return first + second
        }
        override suspend fun sumNullableStrings(first: String?, second: String?, delay: Long): String? {
            if (delay > 0) delay(delay)
            if (first == null && second == null) return null
            return "$first$second"
        }
        override suspend fun crash(message: String, delay: Long) {
            if (delay > 0) delay(delay)
            error(message)
        }
    }

    @Test
    fun testSuspend() = runBlocking {
        check(30 == invokeSumInts(util, 10, 20, 0))
    }

    @Test
    fun testSuspendWithDelay() = runBlocking {
        check(30 == invokeSumInts(util, 10, 20, 1000))
    }

    // Use more complex types
    @Test
    fun testSuspendStrings() = runBlocking {
        check("Hello, world!" == invokeSumStrings(util, "Hello, ", "world!", 100))
    }
    @Test
    fun testSuspendNullableStrings() = runBlocking {
        check(null == invokeSumNullableStrings(util, null, null, 50))
        check("foo:null" == invokeSumNullableStrings(util, "foo:", null, 50))
        check("null:foo" == invokeSumNullableStrings(util, null, ":foo", 50))
        check("foo:foo" == invokeSumNullableStrings(util, "foo:", "foo", 50))
    }

    @Test
    fun testSuspendLists() = runBlocking {
        check(listOf(0, 1, 2, 3) == invokeSumLists(util, listOf(0, 1), listOf(2, 3), 100))
    }

    @Test
    fun testCancellation() = runBlocking(Dispatchers.Default) {
        val res = withTimeoutOrNull(1000) {
            invokeSumInts(util, 0, 0, 2000)
        }
        check(res == null)
    }

    @Test
    fun testFailure() = runBlocking {
        val e = runCatching { invokeCrash(util, "!!!", 100) }.exceptionOrNull()
        check(e?.message != null && e.message!!.contains("!!!"))
    }

}
