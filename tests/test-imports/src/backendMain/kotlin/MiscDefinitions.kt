package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

@KneeEnum typealias DeprecationLevel = kotlin.DeprecationLevel

@Knee var currentDeprecationLevel: DeprecationLevel? = null

@Knee
fun getStrongerDeprecationLevel(level: DeprecationLevel): DeprecationLevel {
    return when (level) {
        DeprecationLevel.WARNING -> DeprecationLevel.ERROR
        DeprecationLevel.ERROR -> DeprecationLevel.HIDDEN
        DeprecationLevel.HIDDEN -> error("Nothing stronger than DeprecationLevel.HIDDEN")
    }
}

