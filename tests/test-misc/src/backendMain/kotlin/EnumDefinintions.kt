package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*

@KneeEnum
enum class Day {
    Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
}

class OuterClass {
    @KneeEnum
    enum class InnerDay {
        Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
    }
}

interface OuterInterface {
    @KneeEnum
    enum class InnerDay {
        Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday
    }
}

@Knee var currentDay: Day? = null
@Knee var currentInnerDay1: OuterClass.InnerDay? = null
@Knee var currentInnerDay2: OuterInterface.InnerDay? = null

@Knee
fun getDayAfter(day: Day): Day {
    if (day == Day.Sunday) return Day.Monday
    else return Day.entries[Day.entries.indexOf(day) + 1]
}

@Knee
fun getAllDays(): List<Day> {
    return Day.entries.toList()
}

@Knee
fun getInnerDay1After(day: OuterClass.InnerDay): OuterClass.InnerDay {
    if (day == OuterClass.InnerDay.Sunday) return OuterClass.InnerDay.Monday
    else return OuterClass.InnerDay.entries[OuterClass.InnerDay.entries.indexOf(day) + 1]
}

@Knee
fun getInnerDay2After(day: OuterInterface.InnerDay): OuterInterface.InnerDay {
    if (day == OuterInterface.InnerDay.Saturday) return OuterInterface.InnerDay.Sunday
    else return OuterInterface.InnerDay.entries[OuterInterface.InnerDay.entries.indexOf(day) + 1]
}

