package io.deepmedia.tools.knee.runtime.buffer

import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.*
import platform.android.jobject

class DoubleBuffer private constructor(private val bytes: ByteBuffer) {

    @PublishedApi internal constructor(environment: JniEnvironment, jobject: jobject) : this(ByteBuffer(
        environment = environment,
        jobject = jobject,
        storage = null,
        freeStorage = { },
        size = environment.getDirectBufferCapacity(jobject).toInt() * 8
    ))

    constructor(environment: JniEnvironment, size: Int) : this(ByteBuffer(
        environment = environment,
        size = size * 8
    ))

    @PublishedApi internal val obj get() = bytes.obj
    val size: Int get() = bytes.size / 8
    val ptr: CArrayPointer<DoubleVar> = bytes.ptr.reinterpret()
    fun free() = bytes.free()
}