package io.deepmedia.tools.knee.runtime.types

import platform.android.*

@PublishedApi
internal fun decodeBoolean(data: jboolean): Boolean = data == JNI_TRUE.toUByte()

@PublishedApi
internal fun encodeBoolean(data: Boolean): jboolean {
    return if (data) JNI_TRUE.toUByte() else JNI_FALSE.toUByte()
}
