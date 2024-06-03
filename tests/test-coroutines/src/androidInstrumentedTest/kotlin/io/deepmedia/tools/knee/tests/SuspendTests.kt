package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.*
import org.junit.Test

class SuspendTests {

    companion object {
        init {
            System.loadLibrary("test_coroutines")
        }
    }

    @Test
    fun testSuspend() = runBlocking {
        check(30 == sumInts(10, 20, 0))
    }

    @Test
    fun testSuspendWithDelay() = runBlocking {
        check(30 == sumInts(10, 20, 1000))
    }

    // Use more complex types
    @Test
    fun testSuspendStrings() = runBlocking {
        check("Hello, world!" == sumStrings("Hello, ", "world!", 100))
    }
    @Test
    fun testSuspendNullableStrings() = runBlocking {
        check(null == sumNullableStrings(null, null, 50))
        check("foo:null" == sumNullableStrings("foo:", null, 50))
        check("null:foo" == sumNullableStrings(null, ":foo", 50))
        check("foo:foo" == sumNullableStrings("foo:", "foo", 50))
    }

    @Test
    fun testSuspendLists() = runBlocking {
        check(listOf(0, 1, 2, 3) == sumLists(listOf(0, 1), listOf(2, 3), 100))
    }

    @Test
    fun testCancellation() = runBlocking(Dispatchers.Default) {
        val res = withTimeoutOrNull(1000) {
            sumInts(0, 0, 2000)
        }
        check(res == null)
    }

    @Test
    fun testFailure() = runBlocking {
        val e = runCatching { crash("!!!", 100) }.exceptionOrNull()
        check(e?.message != null && e.message!!.contains("!!!"))
    }

}
