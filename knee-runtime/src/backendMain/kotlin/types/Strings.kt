package io.deepmedia.tools.knee.runtime.types

import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.*
import platform.android.*

@PublishedApi
internal fun decodeString(env: JniEnvironment, data: jstring): String {
    // The UTF8 version is null terminated so we can pass it to KN without reading length
    // This is not the most efficient though
    // https://developer.android.com/training/articles/perf-jni#utf-8-and-utf-16-strings
    val chars = env.getStringUTFChars(data)
    val str = chars.toKStringFromUtf8()
    env.releaseStringUTFChars(data, chars)
    return str
}

@PublishedApi
internal fun encodeString(env: JniEnvironment, data: String): jstring {
    return env.newStringUTF(data)
}