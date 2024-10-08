package io.deepmedia.tools.knee.tests

import org.junit.Test


class DefaultValuesTests {

    companion object {
        init {
            System.loadLibrary("test_misc")
        }
    }

    @Test
    fun testDefaultValue_function() {
        nullableWithNullDefaultValue()
    }

    @Test
    fun testDefaultValue_class() {
        ConcreteClassWithDefaultValues().withNull()
    }

    @Test
    fun testDefaultValue_emptyString() {
        emptyStringDefaultValue()
    }

    @Test
    fun testDefaultValue_enum() {
        enumDefaultValue()
    }

    @Test
    fun testDefaultValue_float() {
        floatDefaultValue()
    }

    @Test
    fun testDefaultValue_long() {
        floatDefaultValue()
    }

}
