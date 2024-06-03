package io.deepmedia.tools.knee.runtime.buffer

import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.*
import platform.android.jobject

class IntBuffer private constructor(private val bytes: ByteBuffer) {

    @PublishedApi internal constructor(environment: JniEnvironment, jobject: jobject) : this(ByteBuffer(
        environment = environment,
        jobject = jobject,
        storage = null,
        freeStorage = { },
        size = environment.getDirectBufferCapacity(jobject).toInt() * 4
    ))

    constructor(environment: JniEnvironment, size: Int) : this(ByteBuffer(
        environment = environment,
        size = size * 4
    ))

    @PublishedApi internal val obj get() = bytes.obj
    val size: Int get() = bytes.size / 4
    val ptr: CArrayPointer<IntVar> = bytes.ptr.reinterpret()
    fun free() = bytes.free()
}