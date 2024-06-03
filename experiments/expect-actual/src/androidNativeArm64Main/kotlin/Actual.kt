package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*

@Knee
actual fun targetName() = "androidNativeArm64"

actual typealias NativePlatformInfoA = Arm64PlatformInfo

class Arm64PlatformInfo @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeArm64"
}

actual class NativePlatformInfoB @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeArm64"
}