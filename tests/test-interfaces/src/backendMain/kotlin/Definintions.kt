package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.identityHashCode

@KneeInterface
interface Callback {
    var counter: UInt
    val description: String
    fun describe(): String
    fun describeNullable(actuallyDescribe: Boolean?): String?
}

@Knee
lateinit var callback: Callback

@OptIn(ExperimentalNativeApi::class)
@Knee
fun createCallback(): Callback {
    return object : Callback {
        override var counter: UInt = 0u
        override fun describe(): String = "[KN::describe(), ${toString()}]".also { counter++ }
        override fun describeNullable(actuallyDescribe: Boolean?): String? {
            return if (actuallyDescribe == true) describe() else null
        }
        override val description: String get() = "[KN::description, ${toString()}]"
        override fun toString(): String = "Item@${this.identityHashCode()}"
        override fun hashCode(): Int = 333333
    }
}

@Knee
fun invokeCallbackDescribe(callback: Callback): String {
    return callback.describe()
}

@Knee
fun invokeCallbackDescribeNullable(callback: Callback, actuallyDescribe: Boolean?): String? {
    return callback.describeNullable(actuallyDescribe)
}

@Knee
fun invokeCallbackDescription(callback: Callback): String {
    return callback.description
}

@Knee
fun invokeCallbackHashCode(callback: Callback): Int {
    return callback.hashCode()
}

@Knee
fun invokeCallbackToString(callback: Callback): String {
    return callback.toString()
}

@Knee
fun invokeCallbackEquals(c0: Callback, c1: Callback): Boolean {
    return c0 == c1
}

@Knee
fun invokeCallbackGetCounter(callback: Callback): UInt {
    return callback.counter
}

@Knee
fun invokeCallbackSetCounter(callback: Callback, value: UInt) {
    callback.counter = value
}

class Outer {
    @KneeInterface
    interface Inner {
        var value: Int
    }
}

@Knee
fun setInnerValue(inner: Outer.Inner, value: Int) {
    inner.value = value
}

@Knee
fun getInnerValue(inner: Outer.Inner): Int {
    return inner.value
}

@Knee
fun makeInner(initialValue: Int): Outer.Inner {
    return object : Outer.Inner {
        override var value: Int = initialValue
    }
}
