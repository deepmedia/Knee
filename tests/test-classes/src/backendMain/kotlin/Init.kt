package io.deepmedia.tools.knee.tests

import io.deepmedia.tools.knee.annotations.*
import io.deepmedia.tools.knee.runtime.JavaVirtualMachine
import io.deepmedia.tools.knee.runtime.attachCurrentThread
import io.deepmedia.tools.knee.runtime.module.KneeModule
import io.deepmedia.tools.knee.runtime.useEnv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi


@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName(externName = "JNI_OnLoad")
fun onLoad(vm: JavaVirtualMachine): Int {
    vm.useEnv { io.deepmedia.tools.knee.runtime.initKnee(it) }
    return 0x00010006
}
