package io.deepmedia.tools.knee.runtime

import io.deepmedia.tools.knee.runtime.compiler.*
import io.deepmedia.tools.knee.runtime.compiler.initBoxMethods
import io.deepmedia.tools.knee.runtime.compiler.initBuffers
import io.deepmedia.tools.knee.runtime.compiler.initExceptions
import io.deepmedia.tools.knee.runtime.compiler.initInstances
import io.deepmedia.tools.knee.runtime.compiler.initSuspend
import io.deepmedia.tools.knee.runtime.module.KneeModule
import kotlinx.atomicfu.atomic

private val kneeInitialized = atomic(0L)

internal var initializationData: InitializationData? = null

internal class InitializationData(
    val jvm: JavaVirtualMachine,
    val exceptions: MutableSet<SerializableException>
)

fun initKnee(environment: JniEnvironment, vararg modules: KneeModule) {
    val vm = environment.javaVM
    val id = vm.rawValue.toLong()
    val oldId = kneeInitialized.getAndSet(id)
    if (id != oldId) {
        initializationData = InitializationData(vm, mutableSetOf())
        initSuspend(environment)
        initInstances(environment)
        initBoxMethods(environment)
        initExceptions(environment)
        initBuffers(environment)
    }
    val data = initializationData!!
    modules.forEach {
        it.collectExceptions(data.exceptions)
        it.initializeIfNeeded(environment)
    }
}