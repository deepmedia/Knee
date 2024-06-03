package io.deepmedia.tools.knee.plugin.gradle.utils

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget


internal val KotlinNativeTarget.androidAbi get() = when (konanTarget) {
    is KonanTarget.ANDROID_ARM32 -> "armeabi-v7a"
    is KonanTarget.ANDROID_ARM64 -> "arm64-v8a"
    is KonanTarget.ANDROID_X64 -> "x86_64"
    is KonanTarget.ANDROID_X86 -> "x86"
    else -> error("Unknown KonanTarget $konanTarget")
}

internal fun Project.configureAndroidExtension(block: (CommonExtension<*, *, *, *, *>) -> Unit) {
    fun runBlock() {
        val android = extensions.getByName<CommonExtension<*, *, *, *, *>>("android")
        block(android)
    }
    plugins.withId("com.android.application") { runBlock() }
    plugins.withId("com.android.library") { runBlock() }
}