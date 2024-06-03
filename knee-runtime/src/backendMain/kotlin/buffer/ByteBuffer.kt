package io.deepmedia.tools.knee.runtime.buffer

import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.compiler.setDirectBufferNativeOrder
import kotlinx.cinterop.*
import platform.android.jobject
import kotlin.native.ref.createCleaner

class ByteBuffer internal constructor(
    environment: JniEnvironment,
    jobject: jobject,
    storage: CArrayPointer<ByteVar>?,
    private val freeStorage: (CArrayPointer<ByteVar>) -> Unit,
    size: Int?
) {

    /**
     * Called by the compiler when a buffer comes from JVM world.
     * The [ByteBuffer] class here will create a strong reference out of the object
     * and automatically delete it when this buffer is garbage collected.
     */
    @Suppress("unused")
    @PublishedApi
    internal constructor(environment: JniEnvironment, jobject: jobject) : this(
        environment = environment,
        jobject = jobject,
        storage = null,
        freeStorage = { },
        size = null
    )

    /**
     * Can be called by users to allocate a new [ByteBuffer] natively.
     * It can be later be passed to JVM, and it will remain valid until [ByteBuffer.free] is called.
     * After free, the buffer should not be accessed from JVM - data will be undefined, may crash.
     * See `NewDirectByteBuffer` docs.
     */
    constructor(environment: JniEnvironment, size: Int) : this(
        environment = environment,
        storage = nativeHeap.allocArray(size),
        freeStorage = { nativeHeap.free(it) },
        size = size
    )

    /**
     * Can be called by users to allocate a new [ByteBuffer] natively.
     * It can be later be passed to JVM, and it will remain valid until the storage is valid.
     * [freeStorage] should free the storage and make it invalid.
     */
    constructor(
        environment: JniEnvironment,
        size: Int,
        storage: CArrayPointer<ByteVar>,
        freeStorage: (CArrayPointer<ByteVar>) -> Unit,
    ) : this(
        environment = environment,
        jobject = environment.newDirectByteBuffer(storage, size.toLong()).also {
            //https://bugs.openjdk.org/browse/JDK-5043362
            // need to call order(nativeOrder()) here, otherwise ByteBuffer is always BIG_ENDIAN
            // when seen from the JVM side. Without it JVM users are required to do order() before reads/writes
            environment.setDirectBufferNativeOrder(it)
        },
        storage = storage,
        freeStorage = freeStorage,
        size = size
    )

    private val jvm = environment.javaVM

    @PublishedApi
    internal val obj = environment.newGlobalRef(jobject).also {
        // TODO: review this usage of deleteLocalRef, not clear it's needed. See other usages
        environment.deleteLocalRef(jobject)
    }

    // number of elements, not necessarily bytes
    val size: Int = size ?: environment.getDirectBufferCapacity(obj).toInt()

    @Suppress("UNCHECKED_CAST")
    val ptr: CArrayPointer<ByteVar> = storage
        ?: environment.getDirectBufferAddress(obj) as CArrayPointer<ByteVar>

    @Suppress("unused")
    private val objCleaner = createCleaner(this.jvm to this.obj) { (jvm, it) ->
        val env = jvm.env ?: jvm.attachCurrentThread()
        env.deleteGlobalRef(it)
    }

    // don't want to add all Buffer (questionable) functions, let's just expose pointer instead.
    // operator fun set(index: Int = 0, value: Byte) { ptr[index] = value }
    // operator fun get(index: Int = 0): Byte { return ptr[index] }

    fun free() {
        freeStorage(ptr)
    }
}