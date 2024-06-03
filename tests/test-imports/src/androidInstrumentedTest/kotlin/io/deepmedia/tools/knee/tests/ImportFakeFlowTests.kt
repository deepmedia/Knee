package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

class ImportFakeFlowTests {

    companion object {
        init {
            System.loadLibrary("test_imports")
        }
    }

    @Test
    fun testFakeFlow_nativeCollect() = runBlocking {
        val realFlow = flow<String> {
            delay(50)
            emit("Hello from")
            delay(100)
            emit("JVM!")
        }
        val fakeFlow = object : io.deepmedia.tools.knee.tests.FakeFlow<String> {
            override suspend fun collect(collector: io.deepmedia.tools.knee.tests.FakeFlowCollector<String>) {
                realFlow.collect { collector.emit(it) }
            }
        }
        val result = collectFakeFlow(fakeFlow)
        check(result == "Hello from JVM!")
    }

    @Test
    fun testFakeFlow_jvmCollect() = runBlocking {
        val flow = makeFakeFlow()
        var result = ""
        flow.collect {
            result += it
            result += " "
        }
        result = result.trim()
        check(result == "Hello from KN!")
    }

    @Test
    fun testFakeSharedFlow_nativeCollect() = runBlocking {
        val realFlow = flow<String> {
            delay(50)
            emit("Hello JVM")
        }.shareIn(CoroutineScope(EmptyCoroutineContext), SharingStarted.Lazily, 1)
        val fakeFlow = object : FakeSharedFlow<String> {
            override suspend fun collect(collector: FakeFlowCollector<String>): Nothing {
                realFlow.collect { collector.emit(it) }
            }
        }
        val result = collectFakeSharedFlow(fakeFlow)
        check(result == "Hello JVM")
    }

    @Test
    fun testFakeSharedFlow_jvmCollect() = runBlocking {
        val flow = makeFakeSharedFlow()
        var first: String? = null
        try {
            flow.collect {
                first = it
                throw CancellationException("FOUND!")
            }
        } catch (e: CancellationException) {
            if (e.message != "FOUND!") throw e
        }
        check(first!! == "Hello")
    }

    @Test
    fun testFakeMutableSharedFlow() = runBlocking {
        // val flow = MutableSharedFlow<String>(replay = 2, onBufferOverflow = BufferOverflow.SUSPEND)
        val flow = makeFakeMutableSharedFlow(replay = 2, onBufferOverflow = BufferOverflow.SUSPEND)
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
    fun testFakeStateFlow() = runBlocking {
        val flow = makeFakeMutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.SUSPEND)
        flow.emit("Value")

        val counts = mutableListOf<Int>()
        val observer = launch(Dispatchers.IO) {
            flow.subscriptionCount.collect {
                println("[JVM] subscriptionCount CHANGED to $it")
                counts.add(it) }
        }

        val subscriptions = launch(Dispatchers.IO) {
            fun addSubscriber(delay: Long = 0): Job = launch(start = CoroutineStart.UNDISPATCHED) {
                delay(delay)
                println("[JVM] ADDING SUBSCRIBER")
                coroutineContext.job.invokeOnCompletion {
                    println("[JVM] REMOVING SUBSCRIBER")
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
        check(counts == listOf(0, 1, 2, 3, 2, 1, 0)) { "Counts=$counts" }
        coroutineContext.cancelChildren()
    }
}
