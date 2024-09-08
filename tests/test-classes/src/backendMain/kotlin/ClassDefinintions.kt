package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlin.random.Random
import kotlin.random.nextUInt

@KneeClass
class Counter @Knee constructor(initialValue: UInt) {
    var value: UInt = initialValue

    @Knee constructor() : this(initialValue = Random.nextUInt())
    @Knee fun increment() { value += 1u }
    @Knee fun decrement() { value -= 1u }
    @Knee fun add(delta: UInt) { value += delta }
    @Knee fun get(): UInt = value
    @Knee fun flip(bool: Boolean): Boolean = !bool
    override fun toString(): String = "Counter($value)"
    override fun hashCode(): Int = value.toInt()
    override fun equals(other: Any?): Boolean {
        return other is Counter && other.value == value
    }
}

@Knee var currentCounter: Counter? = null

@Knee fun isCurrentCounter(counter: Counter, checkIdentity: Boolean): Boolean {
    if (checkIdentity) return counter === currentCounter
    return counter == currentCounter
}

@Knee fun arrayOfCounters(size: Int): Array<Counter> {
    return Array(size) { Counter() }
}

interface Outer {

    @KneeClass
    class InnerCounter @Knee constructor(initialValue: UInt) {
        var value: UInt = initialValue

        @Knee constructor() : this(initialValue = Random.nextUInt())
        @Knee fun increment() { value += 1u }
        @Knee fun decrement() { value -= 1u }
        @Knee fun add(delta: UInt) { value += delta }
        @Knee fun get(): UInt = value
        override fun toString(): String = "Outer.InnerCounter($value)"
        override fun hashCode(): Int = value.toInt()
        override fun equals(other: Any?): Boolean {
            return other is InnerCounter && other.value == value
        }
    }
}

@Knee var currentInnerCounter: Outer.InnerCounter? = null

@Knee fun isCurrentInnerCounter(counter: Outer.InnerCounter, checkIdentity: Boolean): Boolean {
    if (checkIdentity) return counter === currentInnerCounter
    return counter == currentInnerCounter
}