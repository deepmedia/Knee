package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*


@KneeInterface typealias SimpleLambda = () -> Unit
@KneeInterface typealias ComplexLambda = (String, Long) -> String
// The point of ComplexLambda2 is to test two identical lambdas with different generics
@KneeInterface typealias ComplexLambda2 = (Int, UInt) -> String

@KneeInterface typealias SimpleSuspendLambda = suspend () -> Unit
@KneeInterface typealias ComplexSuspendLambda = suspend (String, Int) -> ULong

@Knee lateinit var currentSimpleLambda: () -> Unit
@Knee fun makeSimpleLambda(): () -> Unit = { }
@Knee fun invokeSimpleLambda(lambda: () -> Unit) { lambda.invoke() }

@Knee lateinit var currentComplexLambda: (String, Long) -> String
@Knee fun makeComplexLambda(): (String, Long) -> String = { a, b -> a + b }
@Knee fun invokeComplexLambda(lambda: (String, Long) -> String, arg0: String, arg1: Long): String {
    val result = lambda.invoke(arg0, arg1)
    return result
}

@Knee lateinit var currentComplexLambda2: (Int, UInt) -> String
@Knee fun makeComplexLambda2(): (Int, UInt) -> String = { a, b -> (a + b.toInt()).toString() }
@Knee fun invokeComplexLambda2(lambda: (Int, UInt) -> String, arg0: Int, arg1: UInt): String {
    return lambda.invoke(arg0, arg1)
}

@Knee lateinit var currentSimpleSuspendLambda: suspend () -> Unit
@Knee fun makeSimpleSuspendLambda(): suspend () -> Unit = { }
@Knee suspend fun invokeSimpleSuspendLambda(lambda: suspend () -> Unit) {
    kotlinx.coroutines.delay(500)
    lambda.invoke()
}

@Knee lateinit var currentSuspendComplexLambda: suspend (String, Int) -> ULong
@Knee fun makeSuspendComplexLambda(): suspend (String, Int) -> ULong = { a, b -> (a.length + b).toULong() }
@Knee suspend fun invokeSuspendComplexLambda(lambda: suspend (String, Int) -> ULong, arg0: String, arg1: Int): ULong {
    kotlinx.coroutines.delay(500)
    return lambda.invoke(arg0, arg1)
}
