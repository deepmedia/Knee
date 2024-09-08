package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlin.random.Random
import kotlin.random.nextUInt

@KneeObject
object TopLevelObject {
    @Knee var value: Int = 0
    @Knee fun reset() { value = 0 }
    @Knee fun increment() { value += 1 }
    @Knee fun decrement() { value -= 1 }
    @Knee override fun toString(): String = "TopLevelObject($value)"
}
