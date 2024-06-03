package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.delay


@Knee
suspend fun sumInts(first: Int, second: Int, delay: Long): Int {
    if (delay > 0) kotlinx.coroutines.delay(delay)
    return first + second
}

@Knee
suspend fun sumStrings(first: String, second: String, delay: Long): String {
    if (delay > 0) kotlinx.coroutines.delay(delay)
    return first + second
}

@Knee
suspend fun sumLists(first: List<Int>, second: List<Int>, delay: Long): List<Int> {
    if (delay > 0) kotlinx.coroutines.delay(delay)
    return first + second
}

@Knee
suspend fun sumNullableStrings(first: String?, second: String?, delay: Long): String? {
    if (delay > 0) kotlinx.coroutines.delay(delay)
    if (first == null && second == null) return null
    return "$first$second"
}

@Knee
suspend fun crash(message: String, delay: Long): Unit {
    if (delay > 0) kotlinx.coroutines.delay(delay)
    error(message)
}