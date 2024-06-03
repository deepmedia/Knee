@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime.types

import kotlin.enums.enumEntries

@OptIn(ExperimentalStdlibApi::class)
@PublishedApi
internal inline fun <reified T: Enum<T>> decodeEnum(index: Int): T {
    return enumEntries<T>()[index]
}

@PublishedApi
internal fun <T: Enum<T>> encodeEnum(instance: T): Int {
    return instance.ordinal
}
