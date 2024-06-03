package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@KneeInterface typealias StringFlowCollector = FlowCollector<String>
@KneeInterface typealias StringFlow = Flow<String>
@KneeInterface typealias StringSharedFlow = SharedFlow<String>
@KneeInterface typealias StringMutableSharedFlow = MutableSharedFlow<String>
@KneeInterface typealias StringStateFlow = StateFlow<String>
@KneeInterface typealias IntStateFlow = StateFlow<Int>
@KneeInterface typealias IntStateFlowCollector = FlowCollector<Int>
@KneeInterface typealias StringMutableStateFlow = MutableStateFlow<String>

@Knee fun makeFlow(): Flow<String> {
    return flow {
        delay(100)
        emit("Hello")
        delay(80)
        emit("from KN!")
    }
}
@Knee fun makeSharedFlow(): SharedFlow<String> {
    return makeFlow().shareIn(CoroutineScope(EmptyCoroutineContext), SharingStarted.Lazily, 1)
}

@Knee suspend fun collectFlow(flow: Flow<String>): String {
    return flow.toList().joinToString(separator = " ")
}

@Knee suspend fun collectSharedFlow(flow: SharedFlow<String>): String {
    return flow.first()
}

@Knee fun makeMutableSharedFlow(replay: Int, onBufferOverflow: BufferOverflow): MutableSharedFlow<String> {
    return MutableSharedFlow<String>(replay = replay, onBufferOverflow = onBufferOverflow)
}
