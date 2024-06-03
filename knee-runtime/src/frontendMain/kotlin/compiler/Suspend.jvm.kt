@file:JvmName("SuspendKt")
package io.deepmedia.tools.knee.runtime.compiler

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("UNCHECKED_CAST")
public class KneeSuspendInvoker<RawResultType>(private val continuation: CancellableContinuation<RawResultType>) {

    private val completed = AtomicBoolean(false)

    @Suppress("unused")
    fun receiveSuccess(result: Any?) {
        if (completed.compareAndSet(false, true)) {
            continuation.resume(result as RawResultType)
        }
    }

    @Suppress("unused")
    fun receiveFailure(exception: Throwable) {
        if (completed.compareAndSet(false, true)) {
            continuation.resumeWithException(exception)
        }
    }

    // @PublishedApi is needed otherwise function gets $knee_runtime suffix
    // Looks like a Kotlin bug
    @PublishedApi
    internal external fun sendCancellation(invocation: Long)

    @PublishedApi
    internal fun sendCancellationSafe(invocation: Long) {
        if (completed.compareAndSet(false, true)) {
            sendCancellation(invocation)
        }
    }
}

@Suppress("unused")
public suspend inline fun <RawResultType> kneeInvokeJvmSuspend(crossinline block: (KneeSuspendInvoker<RawResultType>) -> Long): RawResultType {
    return suspendCancellableCoroutine { cont ->
        val invoker = KneeSuspendInvoker(cont)
        val invocation: Long = block(invoker)
        cont.invokeOnCancellation {
            invoker.sendCancellationSafe(invocation)
        }
    }
}

// REVERSE SUSPEND SUPPORT

private val KneeScope = CoroutineScope(Dispatchers.Default + CoroutineName("Knee"))

// called by codegen JVM code
@Suppress("unused")
public fun <T> kneeInvokeKnSuspend(invoker: Long, block: suspend () -> T): KneeSuspendInvocation<T> {
    return KneeSuspendInvocation(invoker, block)
}

// T = encoded result
public class KneeSuspendInvocation<T>(
    private val invoker: Long,
    block: suspend () -> T
) {
    private val job = KneeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            val result = block()
            sendSuccess(invoker, result)
        } catch (e: Throwable) {
            sendFailure(invoker, e)
        }
    }

    private external fun sendFailure(invoker: Long, error: Throwable)

    private external fun sendSuccess(invoker: Long, value: Any?)

    // Called from K/N
    @Suppress("unused")
    private fun receiveCancellation() {
        job.cancel()
    }
}



