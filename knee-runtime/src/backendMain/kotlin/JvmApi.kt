@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime

import kotlinx.cinterop.*
import platform.android.*

// JNI APIs, with same namings and so on, just wrapped in a more convenient
// fashion, as extensions to CPointer<*>.

typealias JavaVirtualMachine = CPointer<JavaVMVar>

internal val JavaVirtualMachine.api get() = pointed.pointed!!

/**
 * Returns the [JniEnvironment] attached to the current thread. The environment is
 * guaranteed to exist when calling this function from a JNI method, or more generally
 * from JVM-managed threads that have an associated java.lang.Thread.
 * In other cases, this function will return null - [attachCurrentThread] should be called instead.
 */
val JavaVirtualMachine.env: JniEnvironment? get() = memScoped {
    val envPointer: CPointerVar<JNIEnvVar> = allocPointerTo()
    val res = api.GetEnv!!(this@env, envPointer.ptr.reinterpret(), JNI_VERSION_1_6)
    // NOTE: GetEnv returns JNI_EDETACHED if the current thread is not attached to the VM.
    return if (res == JNI_OK) envPointer.pointed!!.ptr else null
}

fun JavaVirtualMachine.attachCurrentThread(): JniEnvironment = memScoped {
    val envPointer: CPointerVar<JNIEnvVar> = allocPointerTo()
    val res = api.AttachCurrentThread!!(this@attachCurrentThread, envPointer.ptr.reinterpret(), null)
    check(res == JNI_OK) { "AttachCurrentThread failed: $res" }
    envPointer.pointed!!.ptr
}

fun JavaVirtualMachine.detachCurrentThread() {
    val res = api.DetachCurrentThread!!(this)
    check(res == JNI_OK) { "DetachCurrentThread failed: $res" }
}
