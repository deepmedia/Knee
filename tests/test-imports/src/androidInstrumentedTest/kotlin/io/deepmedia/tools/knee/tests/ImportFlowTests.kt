package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

class ImportFlowTests {

    companion object {
        init {
            System.loadLibrary("test_imports")
        }
    }

    @Test
    fun testFlow_nativeCollect() = runBlocking {
        val flow = flow<String> {
            delay(50)
            emit("Hello from")
            delay(100)
            emit("JVM!")
        }
        val result = collectFlow(flow)
        check(result == "Hello from JVM!")
    }

    @Test
    fun testFlow_jvmCollect() = runBlocking {
        val flow = makeFlow()
        val result = flow.toList().joinToString(separator = " ")
        check(result == "Hello from KN!")
    }

    @Test
    fun testSharedFlow_nativeCollect() = runBlocking {
        val flow = flow<String> {
            delay(50)
            emit("Hello JVM")
        }.shareIn(CoroutineScope(EmptyCoroutineContext), SharingStarted.Lazily, 1)
        val result = collectSharedFlow(flow)
        check(result == "Hello JVM")
    }

    /**
     * A tricky one. Flow first will do:
     * - JVM: Flow.collectWhile called
     * - JVM: Flow.collect(FlowCollector) called
     * - KN:  Flow.collect(FlowCollector) called
     * - KN:  As result come, FlowCollector.emit() called
     * - JVM: FlowCollector.emit() called
     *        due to logic in collectWhile, an AbortFlowException is thrown
     * - JVM: exception is passed to KN as message+cancellation
     * - KN:  exception is reconstructed as CancellationException(message)
     *        and it is thrown by FlowCollector.emit()
     * - KN:  Flow.collect(FlowCollector) rethrows the exception
     * - KN:  Exception is transformed to some new jthrowable
     * - JVM: Throws with jthrowable
     * So the exception is converted twice and the original information is lost.
     * TODO: we need to keep the jthrowable
     *  When we receive the jthrowable, create a Throwable out of it as we do
     *  but also store the jthrowable somewhere, for example in the cause.
     *  Then when the Throwable must become a jthrowable, we should check if there's
     *  a jthrowable already in the cause, and if there is, just throw that.
     *
     * TODO: care about the opposite case too (testSharedFlow_nativeCollect)
     *  This will be more tricky, we must use StableRef instead of global ref and do from jni
     *  but the principle remains the same.
     *
     */
    @Test
    fun testSharedFlow_jvmCollect() = runBlocking {
        val flow = makeSharedFlow()
        val first = flow.first()
        check(first == "Hello")
    }

    @Test
    fun testMutableSharedFlow() = runBlocking {
        val flow = makeMutableSharedFlow(replay = 2, onBufferOverflow = BufferOverflow.SUSPEND)
        flow.emit("Str0")
        flow.emit("Str1")
        // Add a collector so that tryEmit can return false
        // The collector immediately takes the first item then hangs
        launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            flow.collect { awaitCancellation() }
        }
        check(flow.tryEmit("Str2"))
        check(!flow.tryEmit("Invalid")) { "Flow buffer should be full" }
        val list = mutableListOf<String>()
        withTimeoutOrNull(200) {
            flow.collect { list.add(it) }
            Unit
        }
        check(list.size == 2)
        check(list[0] == "Str1")
        check(list[1] == "Str2")
        coroutineContext.cancelChildren()
    }

    @Test
    fun testStateFlow() = runBlocking {
        val flow = makeMutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.SUSPEND)
        flow.emit("Value")

        val counts = mutableListOf<Int>()
        val observer = launch(Dispatchers.IO) {
            flow.subscriptionCount.collect { counts.add(it) }
        }

        val subscriptions = launch(Dispatchers.IO) {
            fun addSubscriber(delay: Long = 0): Job = launch(start = CoroutineStart.UNDISPATCHED) {
                delay(delay)
                println("ADDING SUBSCRIBER")
                coroutineContext.job.invokeOnCompletion {
                    println("REMOVING SUBSCRIBER")
                }
                flow.collect { awaitCancellation() }
            }
            delay(100)
            val job0 = addSubscriber()
            delay(100)
            val job1 = addSubscriber()
            delay(100)
            val job2 = addSubscriber()
            delay(100)
            job0.cancel()
            delay(100)
            job1.cancel()
            delay(100)
            job2.cancel()
        }

        subscriptions.join()
        delay(50)
        observer.cancel()
        check(counts == listOf(0, 1, 2, 3, 2, 1, 0))
        coroutineContext.cancelChildren()
    }
}
