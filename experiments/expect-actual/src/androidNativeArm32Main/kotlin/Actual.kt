package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*

@Knee
actual fun targetName() = "androidNativeArm32"

actual typealias NativePlatformInfoA = Arm32PlatformInfo

class Arm32PlatformInfo @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeArm32"
}

actual class NativePlatformInfoB @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeArm32"
}

@KneeClass
actual class FunctionWithDefaultParameter {
    @Knee
    actual fun doSomethingWithDefaultParameter(parameter: Int) { }
}