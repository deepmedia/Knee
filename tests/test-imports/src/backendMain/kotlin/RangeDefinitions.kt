package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

@KneeInterface typealias ClosedFloatRange = ClosedRange<Float>

@Knee var currentFloatRange: ClosedRange<Float> = 0F .. 10F

