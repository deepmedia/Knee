package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlin.native.identityHashCode


@Knee fun sumInts(first: Int, second: Int): Int = first + second
@Knee fun sumFloats(first: Float, second: Float): Float = first + second
@Knee fun sumDoubles(first: Double, second: Double): Double = first + second
@Knee fun sumLongs(first: Long, second: Long): Long = first + second
@Knee fun sumBytes(first: Byte, second: Byte): Byte = (first + second).toByte()
@Knee fun sumStrings(first: String, second: String): String = (first + second)

@Knee fun sumUInts(first: UInt, second: UInt): UInt = first + second
@Knee fun sumULongs(first: ULong, second: ULong): ULong = first + second
@Knee fun sumUBytes(first: UByte, second: UByte): UByte = (first + second).toUByte()

// Bools with default value was triggering a bug in the bast
@Knee fun andBooleans(first: Boolean = true, second: Boolean = true): Boolean = first && second
@Knee fun orBooleans(first: Boolean, second: Boolean): Boolean = first || second

@Knee fun sumIntArrays(first: IntArray, second: IntArray): IntArray = first + second
@Knee fun sumFloatArrays(first: FloatArray, second: FloatArray): FloatArray = first + second
@Knee fun sumDoubleArrays(first: DoubleArray, second: DoubleArray): DoubleArray = first + second
@Knee fun sumLongArrays(first: LongArray, second: LongArray): LongArray = first + second
@Knee fun sumByteArrays(first: ByteArray, second: ByteArray): ByteArray = first + second
@Knee fun sumBooleanArrays(first: BooleanArray, second: BooleanArray): BooleanArray = first + second
@Knee fun sumStringArrays(first: Array<String>, second: Array<String>): Array<String> = first + second

@Knee fun sumIntLists(first: List<Int>, second: List<Int>): List<Int> = first + second
@Knee fun sumFloatLists(first: List<Float>, second: List<Float>): List<Float> = first + second
@Knee fun sumDoubleLists(first: List<Double>, second: List<Double>): List<Double> = first + second
@Knee fun sumLongLists(first: List<Long>, second: List<Long>): List<Long> = first + second
@Knee fun sumByteLists(first: List<Byte>, second: List<Byte>): List<Byte> = first + second
@Knee fun sumBooleanLists(first: List<Boolean>, second: List<Boolean>): List<Boolean> = first + second
@Knee fun sumStringLists(first: List<String>, second: List<String>): List<String> = first + second

@Knee fun printNullableInt(data: Int?): String = "$data"
@Knee fun printNullableString(data: String?): String = "$data"
@Knee fun printNullableBoolean(data: Boolean?): String = "$data"
@Knee fun printNullableByteArray(array: ByteArray?): String = "${array?.toList()}"
@Knee fun printNullableBooleanList(list: List<Boolean>?): String = "$list"
@Knee fun getNullableListSize(list: List<Int>?): Int? = list?.size
@Knee fun createNullableList(arg0: Int?, arg1: Int?, arg2: Int?): List<Int>? = listOfNotNull(arg0, arg1, arg2).takeIf { it.isNotEmpty() }
