@file:JvmName("ExceptionsKt")
package io.deepmedia.tools.knee.runtime.compiler

import java.nio.ByteBuffer

@Suppress("unused")
public class KneeKnExceptionToken(val reference: Long) : RuntimeException() {
    protected fun finalize() {
        clear(reference)
    }
    private external fun clear(reference: Long)

    private companion object {
        @JvmStatic
        @Suppress("unused")
        private fun get(throwable: Throwable): Long {
            val cause = throwable.cause as? KneeKnExceptionToken ?: return 0
            return cause.reference
        }
    }
}






