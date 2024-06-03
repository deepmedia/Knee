package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.android.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private lateinit var cancelInvocationMethod: jmethodID

internal fun initSuspend(environment: JniEnvironment) {
    environment.registerNatives(
        classFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvoker",
        bindings = arrayOf(JniNativeMethod(
            name = "sendCancellation",
            signature = "(J)V",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, Unit> { _, _, addr ->
                KneeSuspendInvocation.cancel(addr)
            }
        ))
    )
    cancelInvocationMethod = MethodIds.get(
        env = environment,
        classFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvocation",
        methodName = "receiveCancellation",
        methodSignature = "()V",
        static = false
    )
    // private external fun sendFailure(invoker: Long, error: String, cancellation: Boolean) => (JLjava/lang/String;Z)V
    // private external fun sendFailure(invoker: Long, error: Throwable) => (JLjava/lang/Throwable;)V
    environment.registerNatives(
        classFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvocation",
        bindings = arrayOf(
            JniNativeMethod(
                name = "sendFailure",
                signature = "(JLjava/lang/Throwable;)V",
                pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, jthrowable, Unit> { env, _, invoker, error ->
                    val invokerObject = runCatching {
                        invoker.toCPointer<CPointed>()!!.asStableRef<KneeSuspendInvoker<*, *>>().get()
                    }.getOrNull() ?: return@staticCFunction
                    invokerObject.receiveFailure(env, error)
                }
            ),
            JniNativeMethod(
                name = "sendSuccess",
                signature = "(JLjava/lang/Object;)V",
                pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, jobject, Unit> { env, _, invoker, genericResult ->
                    val invokerObject = runCatching {
                        invoker.toCPointer<CPointed>()!!.asStableRef<KneeSuspendInvoker<jobject, *>>().get()
                    }.getOrNull() ?: return@staticCFunction
                    invokerObject.receiveSuccess(env, genericResult)
                }
            )
        )
    )
}

// REGULAR SUSPEND SUPPORT (JvmSuspend)

private val KneeScope = CoroutineScope(Dispatchers.Unconfined + CoroutineName("Knee"))

@Suppress("unused")
@PublishedApi
internal fun <JniReturnType, LocalReturnType> kneeInvokeJvmSuspend(
    environment: JniEnvironment,
    invoker: jobject,
    // jniReturnTypeSignature: String, // JNI signature of JniReturnType, e.g. Ljava/lang/Object;
    block: suspend () -> LocalReturnType,
    encoder: (JniEnvironment, LocalReturnType) -> JniReturnType,
): Long = KneeSuspendInvocation(environment, invoker, block, encoder).address

private class KneeSuspendInvocation<Jni, Local>(
    environment: JniEnvironment,
    invoker: jobject,
    block: suspend () -> Local,
    encoder: (JniEnvironment, Local) -> Jni,
) {
    private val jvm = environment.javaVM
    private val selfRef = StableRef.create(this)
    private val invokerRef = environment.newGlobalRef(invoker)

    val address = selfRef.asCPointer().toLong()

    // Early fetch the jmethods because it's not safe to do it in useEnv { } later,
    // the JVM might use the wrong class loader:
    // https://developer.android.com/training/articles/perf-jni#faq:-why-didnt-findclass-find-my-class
    private val complete = getReceiveSuccessMethod(environment) // , jniTypeSignature)
    private val fail = getReceiveFailureMethod(environment)
    // private val returnsUnit = jniTypeSignature.isEmpty()

    // Note: undispatched is very important, block() needs the same thread so that JniEnvironment
    // is still valid for example.
    private val job = KneeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            val result = block()
            jvm.useEnv { env ->
                val encoded = encoder(env, result)
                env.callVoidMethod(invokerRef, complete, encoded)
            }
        } catch (e: Throwable) {
            jvm.useEnv { env ->
                val jthrowable = env.processNativeException(e)
                env.callVoidMethod(invokerRef, fail, jthrowable)
                // val message = env.newStringUTF("Native failure: ${e.message}")
                // val cancellation: jboolean = if (e is CancellationException) 1u else 0u
                // env.callVoidMethod(invokerRef, fail, message, cancellation)
            }
        } finally {
            dispose()
        }
    }

    fun dispose() {
        runCatching { selfRef.dispose() }
        runCatching { jvm.useEnv { it.deleteGlobalRef(invokerRef) } }
    }

    companion object {
        /**
         * TODO: this is not 100% safe, passing a disposed address here can cause segfault
         *  The biggest problem is that neither asStableRef nor StableRef.get() fail. Only when we dereference
         *  the invocation the system fails with a segmentation fault.
         *
         * Fixed the most frequent crash by checking for cancellation.isCompleted in JVM's KneeSuspendInvoker.
         * So it won't even send the cancel call if it already received a result.
         *
         * There is still margin for improvement though because in a concurrent scenario, we might
         * dispose the stable ref while this function is being executed (or slightly before).
         * Check KneeSuspendInvoker.sendCancellationSafe().
         */
        fun cancel(address: Long) = runCatching {
            val ref = address.toCPointer<CPointed>()!!.asStableRef<KneeSuspendInvocation<*,*>>()
            val inv: KneeSuspendInvocation<*,*> = ref.get()
            inv.job.cancel()
        }

        // This is an extra layer of cache, should be faster than creating MethodKeys for each call
        // we have a very limited set of functions that we want to store. Does this make sense? IDK
        private const val invokerFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvoker"
        // private val receiveSuccessMethods = mutableMapOf<String, jmethodID>()
        private var receiveSuccessMethod: jmethodID? = null
        private var receiveFailureMethod: jmethodID? = null

        /* private fun getReceiveSuccessMethod(environment: JniEnvironment, rawTypeSignature: String): jmethodID {
            return receiveSuccessMethods.getOrPut(rawTypeSignature) {
                MethodIds.get(environment, invokerFqn, "receiveSuccess", "($rawTypeSignature)V", false)
            }
        } */

        private fun getReceiveSuccessMethod(environment: JniEnvironment): jmethodID {
            if (receiveSuccessMethod == null) { // String, Boolean
                receiveSuccessMethod = MethodIds.get(environment, invokerFqn, "receiveSuccess", "(Ljava/lang/Object;)V", false)
            }
            return receiveSuccessMethod!!
        }

        private fun getReceiveFailureMethod(environment: JniEnvironment): jmethodID {
            if (receiveFailureMethod == null) { // String, Boolean
                // receiveFailureMethod = MethodIds.get(environment, invokerFqn, "receiveFailure", "(Ljava/lang/String;Z)V", false)
                receiveFailureMethod = MethodIds.get(environment, invokerFqn, "receiveFailure", "(Ljava/lang/Throwable;)V", false)
            }
            return receiveFailureMethod!!
        }
    }
}

// REVERSE SUSPEND SUPPORT (KnSuspend)

@PublishedApi
@Suppress("unused")
internal suspend fun <Encoded, Decoded> kneeInvokeKnSuspend(
    virtualMachine: JavaVirtualMachine,
    block: (JniEnvironment, Long) -> jobject,
    decoder: (JniEnvironment, Encoded) -> Decoded
): Decoded {
    return suspendCancellableCoroutine { cont ->
        // invoker must decode otherwise we might end up releasing the environment with useEnv before decode
        val invoker = KneeSuspendInvoker(virtualMachine, cont, decoder)
        val invocation: jobject? = virtualMachine.useEnv { env ->
            val weak = block(env, invoker.address)
            if (invoker.completed) null else {
                // TODO: review this usage of deleteLocalRef, not clear it's needed. See other usages
                env.newGlobalRef(weak).also { env.deleteLocalRef(weak) }
            }
        }
        if (invocation != null) {
            cont.invokeOnCancellation {
                invoker.sendCancellation(invocation)
            }
            // Not clear when we should delete the global reference. Not sure if the job has the same lifecycle
            // of this coroutine, might live longer. But not a big issue. On the other hand passing the invocation to invoker
            // to be released during receiveSuccess() or receiveFailure() IS a problem, because we would need a mutex
            // for a 100% safe implementation.
            // Current solution is bad though in that it attaches/detaches the JNI environment an extra time.
            cont.context.job.invokeOnCompletion {
                virtualMachine.useEnv { it.deleteGlobalRef(invocation) }
            }
        }
    }
}

@PublishedApi
internal class KneeSuspendInvoker<Encoded, Decoded>(
    private val jvm: JavaVirtualMachine,
    private val continuation: CancellableContinuation<Decoded>,
    private val decoder: (JniEnvironment, Encoded) -> Decoded
) {

    private val stableRef = StableRef.create(this)

    var completed = false

    val address get() = stableRef.asCPointer().toLong()

    fun receiveFailure(env: JniEnvironment, error: jthrowable) {
        completed = true
        continuation.resumeWithException(env.processJvmException(error))
        stableRef.dispose()
    }

    fun receiveSuccess(env: JniEnvironment, value: Encoded) {
        completed = true
        continuation.resume(decoder(env, value))
        stableRef.dispose()
    }

    fun sendCancellation(invocation: jobject) {
        // don't dispose here: wait for receiveSuccess or receiveFailure
        jvm.useEnv { env -> env.callVoidMethod(invocation, cancelInvocationMethod) }
    }
}

