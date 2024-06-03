package io.deepmedia.tools.knee.tests

import org.junit.Test

class ExceptionTests {

    companion object {
        init {
            System.loadLibrary("test_misc")
        }
    }

    @Test
    fun testExceptionWithNothing() {
        try {
            throwWithNothing("myMessage")
            error("Should not reach")
        } catch (e: Throwable) {
            assert(e.message!!.contains("myMessage"))
        }
    }

    @Test
    fun testExceptionWithUnit() {
        try {
            throwWithUnit("myMessage")
            error("Should not reach")
        } catch (e: Throwable) {
            assert(e.message!!.contains("myMessage"))
        }
    }

    @Test
    fun testCustomException_fromNative() {
        try {
            throwCustomException(123)
            error("Should not reach")
        } catch (e: CustomException) {
            assert(e.code == 123)
        }
    }

    @Test
    fun testCustomException_fromJvm() {
        val thrower = object : CustomExceptionThrower {
            override fun throwCustomException(code: Int) {
                throw CustomException("Exception thrown from JVM.", code)
            }
        }

        val code = throwCustomExceptionWithThrowerAndCatchCode(thrower, 999)
        assert(code == 999)
    }

    @Test
    fun testCustomException_crossInBothDirections() {
        val msg = "Exception crossing the JNI bridge in both directions."
        val thrower = object : CustomExceptionThrower {
            override fun throwCustomException(code: Int) {
                throw CustomException(msg, code)
            }
        }
        try {
            throwCustomExceptionWithThrower(thrower, 345)
            error("Should not reach here.")
        } catch (e: CustomException) {
            assert(e.code == 345) { "Code mismatch: ${e.code}" }
            assert(e.message == msg) { "Message mismatch: ${e.message} != $msg" }
        }
    }

    @Test
    fun testNestedCustomException_fromNative() {
        try {
            throwNestedCustomException(123)
            error("Should not reach")
        } catch (e: CustomException.Nested) {
            assert(e.code == 123)
        }
    }

    @Test
    fun testNestedCustomException_fromJvm() {
        val thrower = object : CustomExceptionThrower.Nested {
            override fun throwNestedCustomException(code: Int) {
                throw CustomException.Nested("Exception thrown from JVM.", code)
            }
        }

        val code = throwNestedCustomExceptionWithThrowerAndCatchCode(thrower, 999)
        assert(code == 999)
    }


    @Test
    fun testNestedCustomException_crossInBothDirections() {
        val msg = "Exception crossing the JNI bridge in both directions."
        val thrower = object : CustomExceptionThrower.Nested {
            override fun throwNestedCustomException(code: Int) {
                throw CustomException.Nested(msg, code)
            }
        }
        try {
            throwNestedCustomExceptionWithThrower(thrower, 345)
            error("Should not reach here.")
        } catch (e: CustomException.Nested) {
            assert(e.code == 345) { "Code mismatch: ${e.code}" }
            assert(e.message == msg) { "Message mismatch: ${e.message} != $msg" }
        }
    }
}
