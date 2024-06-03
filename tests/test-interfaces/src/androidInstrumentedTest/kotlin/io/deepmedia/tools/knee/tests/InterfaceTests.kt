package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode

// TODO: test garbage collection
class InterfaceTests {

    companion object {
        init {
            System.loadLibrary("test_interfaces")
        }
    }

    val jvm = object : Callback {
        override var counter: UInt = 0u
        override val description: String get() = "[JVM::description, ${toString()}]"
        override fun describe(): String = "[JVM::describe(), ${toString()}]".also { counter++ }
        override fun describeNullable(actuallyDescribe: Boolean?): String? {
            return if (actuallyDescribe == true) describe() else null
        }
        override fun hashCode(): Int = 999999
        override fun toString(): String = "Item@${identityHashCode(this)}"
    }

    @Test
    fun testCodecPreservesObject() {
        callback = jvm
        require(callback === jvm)
    }

    @Test
    fun testEncodedFunctions() {
        jvm.counter = 3u
        require(invokeCallbackDescribe(jvm) == jvm.describe())
        require(invokeCallbackDescription(jvm) == jvm.description)
        require(invokeCallbackGetCounter(jvm) == jvm.counter)
    }

    @Test
    fun testEncodedDefaultFunctions() {
        require(invokeCallbackHashCode(jvm) == jvm.hashCode())
        require(invokeCallbackToString(jvm) == jvm.toString())
        require(invokeCallbackEquals(jvm, jvm))
    }

    @Test
    fun testEncodedFunctions_native() {
        val kn = createCallback()
        kn.counter = 5u
        require(invokeCallbackDescribe(kn) == kn.describe())
        require(invokeCallbackDescription(kn) == kn.description)
        require(invokeCallbackGetCounter(kn) == kn.counter)
    }

    @Test
    fun testEncodedDefaultFunctions_native() {
        val kn = createCallback()
        require(invokeCallbackHashCode(kn) == kn.hashCode())
        require(invokeCallbackToString(kn) == kn.toString())
        require(invokeCallbackEquals(kn, kn))
    }


    @Test
    fun testNullableArguments() {
        check(null == invokeCallbackDescribeNullable(jvm, false))
        check(null == invokeCallbackDescribeNullable(jvm, null))
        check(jvm.describe() == invokeCallbackDescribeNullable(jvm, true))

        val kn = createCallback()
        check(null == kn.describeNullable(false))
        check(null == kn.describeNullable(null))
        check(invokeCallbackDescribe(kn) == kn.describeNullable(true))
    }

    @Test
    fun testCounterUpdate() {
        val kn = createCallback()
        // update on JVM, retreive natively
        jvm.counter = 100u
        kn.counter = 100u
        require(jvm.counter == invokeCallbackGetCounter(kn))
        // update on KN, retreive natively
        invokeCallbackSetCounter(jvm, 200u)
        invokeCallbackSetCounter(kn, 200u)
        require(jvm.counter == invokeCallbackGetCounter(kn))
    }

    @Test
    fun testCounterRetreival() {
        val kn = createCallback()
        // update natively, retreive on JVM
        jvm.counter = 300u
        invokeCallbackSetCounter(kn, 300u)
        require(jvm.counter == kn.counter)
        require(jvm.counter == 300u)

        // update natively, retreive on KN
        jvm.counter = 400u
        invokeCallbackSetCounter(kn, 400u)
        require(invokeCallbackGetCounter(jvm) == invokeCallbackGetCounter(kn))
        require(invokeCallbackGetCounter(kn) == 400u)
    }
}
