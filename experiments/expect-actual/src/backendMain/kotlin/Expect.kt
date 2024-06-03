package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.*
import kotlinx.cinterop.ExperimentalForeignApi

expect fun targetName(): String

@OptIn(ExperimentalForeignApi::class)
@Knee
fun jvmToString(): String = currentJavaVirtualMachine.toString()

@KneeClass(name = "PlatformInfoA")
expect class NativePlatformInfoA : PlatformInfo

@KneeClass(name = "PlatformInfoB")
expect class NativePlatformInfoB : PlatformInfo

abstract class PlatformInfo {
    @Knee abstract val targetName: String
}

expect class FunctionWithDefaultParameter {
    fun doSomethingWithDefaultParameter(parameter: Int = 0)
}