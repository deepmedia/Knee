package io.deepmedia.tools.knee.tests

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Test

class ObjectTests {

    companion object {
        init {
            System.loadLibrary("test_classes")
        }
    }

    @Test
    fun testTopLevel_exists() {
        TopLevelObject::class.toString()
    }

    @Test
    fun testTopLevel_toString() {
        TopLevelObject.reset()
        val tl = TopLevelObject.toString()
        check(tl == "TopLevelObject(0)") { tl }
    }

    @Test
    fun testTopLevel_functions() {
        TopLevelObject.reset()
        TopLevelObject.increment()
        TopLevelObject.increment()
        check(TopLevelObject.toString() == "TopLevelObject(2)") { TopLevelObject.toString() }
        TopLevelObject.decrement()
        check(TopLevelObject.toString() == "TopLevelObject(1)") { TopLevelObject.toString() }
    }

    @Test
    fun testTopLevel_property() {
        TopLevelObject.reset()
        TopLevelObject.value = 15
        check(TopLevelObject.value == 15) { TopLevelObject.value }
    }

    @Test
    fun testInner_property() {
        ObjectParent.InnerObject.value = 15
        check(ObjectParent.InnerObject.value == 15) { ObjectParent.InnerObject.value }
    }

    @Test
    fun testCompanion_property() {
        ObjectParent.value = 15
        check(ObjectParent.value == 15) { ObjectParent.value }
    }

}
