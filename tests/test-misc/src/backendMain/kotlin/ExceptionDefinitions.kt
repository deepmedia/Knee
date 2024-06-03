package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*

@Knee
fun throwWithNothing(message: String): Nothing {
 error(message)
}

@Knee
fun throwWithUnit(message: String): Unit {
    error(message)
}

@KneeClass
class CustomException @Knee constructor(message: String, @Knee val code: Int) : RuntimeException(message) {
    @Knee
    override val message: String? get() = super.message

    @KneeClass
    class Nested @Knee constructor(message: String, @Knee val code: Int) : RuntimeException(message) {
        @Knee
        override val message: String? get() = super.message
    }
}

@KneeInterface
interface CustomExceptionThrower {
    fun throwCustomException(code: Int)

    @KneeInterface
    interface Nested {
        fun throwNestedCustomException(code: Int)
    }
}

@Knee
fun throwCustomException(code: Int) {
    throw(CustomException("Custom!", code))
}

@Knee
fun throwCustomExceptionWithThrower(thrower: CustomExceptionThrower, code: Int) {
    try {
        thrower.throwCustomException(code)
        error("Should not reach here.")
    } catch (e: Throwable) {
        if (e !is CustomException) {
            error("Caught other throwable: ${e.message} ${e::class}")
        }
        throw e
    }
}

@Knee
fun throwCustomExceptionWithThrowerAndCatchCode(thrower: CustomExceptionThrower, code: Int): Int {
    try {
        thrower.throwCustomException(code)
        error("Should not reach here.")
    } catch (e: CustomException) {
        return e.code
    } catch (e: Throwable) {
        error("Caught other throwable: ${e.message} ${e::class}")
    }
}

@Knee
fun throwNestedCustomException(code: Int) {
    throw(CustomException.Nested("Custom!", code))
}

@Knee
fun throwNestedCustomExceptionWithThrower(thrower: CustomExceptionThrower.Nested, code: Int) {
    try {
        thrower.throwNestedCustomException(code)
        error("Should not reach here.")
    } catch (e: Throwable) {
        if (e !is CustomException.Nested) {
            error("Caught other throwable: ${e.message} ${e::class}")
        }
        throw e
    }
}

@Knee
fun throwNestedCustomExceptionWithThrowerAndCatchCode(thrower: CustomExceptionThrower.Nested, code: Int): Int {
    try {
        thrower.throwNestedCustomException(code)
        error("Should not reach here.")
    } catch (e: CustomException.Nested) {
        return e.code
    } catch (e: Throwable) {
        error("Caught other throwable: ${e.message} ${e::class}")
    }
}