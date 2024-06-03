package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.buffer.*


/* @Knee
fun simpleBuffer(buffer: ByteBuffer): Unit {
} */

@Knee
fun simpleBufferCallback(callback: () -> ByteBuffer): Unit {
}

/* @Knee
suspend fun suspendingBufferCallback(callback: () -> ByteBuffer): Unit {
}
 */

@KneeInterface typealias BufferCallback = () -> ByteBuffer