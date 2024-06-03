package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.buffer.*


@Knee
fun nullableWithNullDefaultValue(foo: Int? = null) {
}

interface BaseInterfaceWithDefaultValues {
    fun withNull(foo: Int? = null)
}

@KneeClass class ConcreteClassWithDefaultValues @Knee constructor() : BaseInterfaceWithDefaultValues {
    @Knee
    override fun withNull(foo: Int?) {

    }
}