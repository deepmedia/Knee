@file:OptIn(ExperimentalForeignApi::class)

package io.deepmedia.tools.knee.sample

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.currentJavaVirtualMachine
import kotlinx.cinterop.ExperimentalForeignApi
import platform.android.ANDROID_LOG_WARN
import platform.android.__android_log_print

@OptIn(ExperimentalStdlibApi::class)
@CName(externName = "JNI_OnLoad")
@KneeInit
fun initKnee() {
    __android_log_print(ANDROID_LOG_WARN.toInt(), "Sample", "Hello")
    __android_log_print(ANDROID_LOG_WARN.toInt(), "Sample", "Hello $currentJavaVirtualMachine")
}
