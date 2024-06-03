package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*

@Knee
actual fun targetName() = "androidNativeX64"

actual typealias NativePlatformInfoA = X64PlatformInfo

class X64PlatformInfo @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeX64"
}

actual class NativePlatformInfoB @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeX64"
}