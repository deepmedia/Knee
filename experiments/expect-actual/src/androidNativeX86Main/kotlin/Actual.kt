package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*

@Knee
actual fun targetName() = "androidNativeX86"

actual typealias NativePlatformInfoA = X86PlatformInfo

class X86PlatformInfo @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeX86"
}

actual class NativePlatformInfoB @Knee constructor() : PlatformInfo() {
    override val targetName: String = "androidNativeX86"
}