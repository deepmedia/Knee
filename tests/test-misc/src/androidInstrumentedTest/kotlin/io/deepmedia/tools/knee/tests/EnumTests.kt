package io.deepmedia.tools.knee.tests

import org.junit.Test
import java.lang.System.identityHashCode
import kotlin.math.abs

class EnumTests {

    companion object {
        init {
            System.loadLibrary("test_misc")
        }
    }

    @Test
    fun testProperty_topLevel() {
        check(currentDay == null)
        currentDay = Day.Monday
        check(currentDay == Day.Monday)
    }

    @Test
    fun testEncodeDecode_topLevel() {
        check(getDayAfter(Day.Monday) == Day.Tuesday)
        check(getDayAfter(Day.Sunday) == Day.Monday)
    }

    @Test
    fun testList_topLevel() {
        val all = getAllDays()
        check(all.contains(Day.Monday))
        check(all.size == 7)
    }

    @Test
    fun testProperty_insideClass() {
        check(currentInnerDay1 == null)
        currentInnerDay1 = OuterClass.InnerDay.Friday
        check(currentInnerDay1 == OuterClass.InnerDay.Friday)
    }

    @Test
    fun testEncodeDecode_insideClass() {
        check(getInnerDay1After(OuterClass.InnerDay.Saturday) == OuterClass.InnerDay.Sunday)
        check(getInnerDay1After(OuterClass.InnerDay.Monday) == OuterClass.InnerDay.Tuesday)
        check(getInnerDay1After(OuterClass.InnerDay.Sunday) == OuterClass.InnerDay.Monday)
    }

    @Test
    fun testProperty_insideInterface() {
        check(currentInnerDay2 == null)
        currentInnerDay2 = OuterInterface.InnerDay.Wednesday
        check(currentInnerDay2 == OuterInterface.InnerDay.Wednesday)
    }

    @Test
    fun testEncodeDecode_insideInterface() {
        check(getInnerDay2After(OuterInterface.InnerDay.Saturday) == OuterInterface.InnerDay.Sunday)
        check(getInnerDay2After(OuterInterface.InnerDay.Monday) == OuterInterface.InnerDay.Tuesday)
        check(getInnerDay2After(OuterInterface.InnerDay.Sunday) == OuterInterface.InnerDay.Monday)
    }

}
