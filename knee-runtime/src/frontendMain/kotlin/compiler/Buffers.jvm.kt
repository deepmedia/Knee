@file:JvmName("BuffersKt")
package io.deepmedia.tools.knee.runtime.compiler

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("unused")
internal object KneeBuffers {
    @JvmStatic
    private fun setNativeOrder(buffer: ByteBuffer) {
        buffer.order(ByteOrder.nativeOrder())
    }
}







