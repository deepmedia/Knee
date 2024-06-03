package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

// Flow is a big beast to tame so we start with fake definitions.
// These tests also have another purpose which is to ensure that KneeImport can
// deal with custom interfaces (not external) and clone them correctly in codegen

interface FakeFlow<out T> {
    suspend fun collect(collector: FakeFlowCollector<T>)
}

fun interface FakeFlowCollector<in T> {
    suspend fun emit(value: T)
}

interface FakeSharedFlow<out T> : FakeFlow<T> {
    override suspend fun collect(collector: FakeFlowCollector<T>): Nothing
}

interface FakeMutableSharedFlow<T> : FakeSharedFlow<T>, FakeFlowCollector<T> {
    fun tryEmit(value: T): Boolean
    val subscriptionCount: FakeStateFlow<Int>
    fun resetReplayCache()
}

interface FakeStateFlow<out T> : FakeSharedFlow<T> {
    val value: T
}

interface FakeMutableStateFlow<T> : FakeStateFlow<T>, FakeMutableSharedFlow<T> {
    override var value: T
    fun compareAndSet(expect: T, update: T): Boolean
}

@KneeEnum typealias BufferOverflow = kotlinx.coroutines.channels.BufferOverflow
@KneeInterface typealias StringFakeFlowCollector = FakeFlowCollector<String>
@KneeInterface typealias StringFakeFlow = FakeFlow<String>
@KneeInterface typealias StringFakeSharedFlow = FakeSharedFlow<String>
@KneeInterface typealias StringFakeMutableSharedFlow = FakeMutableSharedFlow<String>
@KneeInterface typealias StringFakeStateFlow = FakeStateFlow<String>
@KneeInterface typealias IntFakeStateFlow = FakeStateFlow<Int>
@KneeInterface typealias IntFakeStateFlowCollector = FakeFlowCollector<Int>
@KneeInterface typealias StringFakeMutableStateFlow = FakeMutableStateFlow<String>

private val helloFromKnFlow = flow {
    delay(100)
    emit("Hello")
    delay(80)
    emit("from KN!")
}

@Knee fun makeFakeFlow(): FakeFlow<String> {
    return object : FakeFlow<String> {
        override suspend fun collect(collector: FakeFlowCollector<String>) {
            helloFromKnFlow.collect { collector.emit(it) }
        }
    }
}
@Knee fun makeFakeSharedFlow(): FakeSharedFlow<String> {
    val shared = helloFromKnFlow.shareIn(CoroutineScope(EmptyCoroutineContext), SharingStarted.Lazily, 1)
    return object : FakeSharedFlow<String> {
        override suspend fun collect(collector: FakeFlowCollector<String>): Nothing {
            shared.collect { collector.emit(it) }
        }
    }
}

@Knee suspend fun collectFakeFlow(flow: FakeFlow<String>): String {
    var text = ""
    flow.collect { text += "$it " }
    return text.trim()
}

@Knee suspend fun collectFakeSharedFlow(flow: FakeSharedFlow<String>): String {
    var first: String? = null
    try {
        flow.collect {
            first = it
            throw CancellationException("FOUND!")
        }
    } catch (e: CancellationException) {
        if (e.message != "FOUND!") throw e
    }
    return first!!
}

@Knee fun makeFakeMutableSharedFlow(replay: Int, onBufferOverflow: BufferOverflow): FakeMutableSharedFlow<String> = object : FakeMutableSharedFlow<String> {
    val real = MutableSharedFlow<String>(replay = replay, onBufferOverflow = onBufferOverflow)
    override fun tryEmit(value: String): Boolean = real.tryEmit(value)
    override fun resetReplayCache() = real.resetReplayCache()
    override suspend fun collect(collector: FakeFlowCollector<String>): Nothing {
        println("[NATIVE] collect STARTED")
        real.collect { collector.emit(it) }
    }
    override suspend fun emit(value: String) {
        real.emit(value)
    }
    override val subscriptionCount: FakeStateFlow<Int> = object : FakeStateFlow<Int> {
        override val value: Int get() = real.subscriptionCount.value
        override suspend fun collect(collector: FakeFlowCollector<Int>): Nothing {
            real.subscriptionCount.collect {
                println("[NATIVE] subscriptionCount CHANGED to $it")
                collector.emit(it)
            }
        }
    }
}
