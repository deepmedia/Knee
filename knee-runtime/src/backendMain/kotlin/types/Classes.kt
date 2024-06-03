@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime.types

import kotlinx.cinterop.*

@PublishedApi
internal fun encodeClass(instance: Any): Long {
    return StableRef.create(instance).asCPointer().toLong()
}

@PublishedApi
internal inline fun <reified T: Any> decodeClass(instance: Long): T {
    val pointer = checkNotNull(instance.toCPointer<CPointed>()) {
        "Class reference $instance is invalid!"
    }
    return pointer.asStableRef<T>().get()
}
