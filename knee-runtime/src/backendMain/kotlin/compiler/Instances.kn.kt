package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.*
import platform.android.*


// internal lateinit var kneeWrapInstance: jmethodID // public fun kneeWrapInstance(ref: Long, className: String): Any?
// internal lateinit var kneeUnwrapInstance: jmethodID // public fun kneeUnwrapInstance(instance: Any): Long

private const val InstancesKtFqn = "io.deepmedia.tools.knee.runtime.compiler.InstancesKt"

internal fun initInstances(environment: JniEnvironment) {
    // kneeWrapInstance = MethodIds.get(environment, InstancesKtFqn, "kneeWrapInstance", "(JLjava/lang/String;)Ljava/lang/Object;", true)
    // kneeUnwrapInstance = MethodIds.get(environment, InstancesKtFqn, "kneeUnwrapInstance", "(Ljava/lang/Object;)J", true)
    environment.registerNatives(
        classFqn = InstancesKtFqn,
        JniNativeMethod(
            name = "kneeDisposeInstance",
            signature = "(J)V",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, Unit> { _, _, ref ->
                runCatching { ref.toCPointer<CPointed>()!!.asStableRef<Any>().dispose() }
            }
        ),
        JniNativeMethod(
            name = "kneeDescribeInstance",
            signature = "(J)Ljava/lang/String;",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, jstring> { env, _, ref ->
                ref.toCPointer<CPointed>()!!.asStableRef<Any>().get().toString().let { env.newStringUTF(it) }
            }
        ),
        JniNativeMethod(
            name = "kneeHashInstance",
            signature = "(J)I",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, jint> { env, _, ref ->
                ref.toCPointer<CPointed>()!!.asStableRef<Any>().get().hashCode()
            }
        ),
        JniNativeMethod(
            name = "kneeCompareInstance",
            signature = "(JJ)Z",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, Long, jboolean> { env, _, ref0, ref1 ->
                val i0 = ref0.toCPointer<CPointed>()?.asStableRef<Any>()?.get()
                val i1 = ref1.toCPointer<CPointed>()?.asStableRef<Any>()?.get()
                if (i0 != null && i0 == i1) 1u else 0u
            }
        )
    )
}

