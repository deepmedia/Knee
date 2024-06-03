package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ImportMiscTests {

    companion object {
        init {
            System.loadLibrary("test_imports")
        }
    }

    @Test
    fun testImportedEnumNullableProperty() {
        check(currentDeprecationLevel == null)
        currentDeprecationLevel = DeprecationLevel.HIDDEN
        check(currentDeprecationLevel == DeprecationLevel.HIDDEN)
    }

    @Test
    fun testImportedEnumArgumentsAndReturnType() {
        check(getStrongerDeprecationLevel(DeprecationLevel.WARNING) == DeprecationLevel.ERROR)
        check(getStrongerDeprecationLevel(DeprecationLevel.ERROR) == DeprecationLevel.HIDDEN)
    }
}
