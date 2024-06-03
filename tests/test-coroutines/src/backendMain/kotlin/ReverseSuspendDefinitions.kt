package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.delay

@KneeInterface
interface ReverseUtil {
    suspend fun sumInts(first: Int, second: Int, delay: Long): Int
    suspend fun sumStrings(first: String, second: String, delay: Long): String
    suspend fun sumNullableStrings(first: String?, second: String?, delay: Long): String?
    suspend fun sumLists(first: List<Int>, second: List<Int>, delay: Long): List<Int>
    suspend fun crash(message: String, delay: Long): Unit
}

@Knee
suspend fun invokeSumInts(receiver: ReverseUtil, first: Int, second: Int, delay: Long): Int {
    return receiver.sumInts(first, second, delay)
}

@Knee
suspend fun invokeSumStrings(receiver: ReverseUtil, first: String, second: String, delay: Long): String {
    return receiver.sumStrings(first, second, delay)
}

@Knee
suspend fun invokeSumNullableStrings(receiver: ReverseUtil, first: String?, second: String?, delay: Long): String? {
    return receiver.sumNullableStrings(first, second, delay)
}

@Knee
suspend fun invokeSumLists(receiver: ReverseUtil, first: List<Int>, second: List<Int>, delay: Long): List<Int> {
    return receiver.sumLists(first, second, delay)
}

@Knee
suspend fun invokeCrash(receiver: ReverseUtil, message: String, delay: Long) {
    return receiver.crash(message, delay)
}

