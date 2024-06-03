package io.deepmedia.tools.knee.runtime.module

import io.deepmedia.tools.knee.runtime.JniEnvironment
import io.deepmedia.tools.knee.runtime.JniNativeMethod
import io.deepmedia.tools.knee.runtime.compiler.*
import io.deepmedia.tools.knee.runtime.javaVM
import io.deepmedia.tools.knee.runtime.registerNatives
import kotlinx.atomicfu.atomic

class KneeModuleBuilder internal constructor() {
    internal var initializer: ((JniEnvironment) -> Unit)? = null
    internal val exportAdapters = mutableMapOf<Int, KneeModule.Adapter<*, *>>()

    fun initialize(block: (JniEnvironment) -> Unit) {
        initializer = block
    }

    // export2, handled by plugin and replaced with exportAdapter()
    inline fun <reified T: Any> export() {
        error("export() error. Is Knee compiler plugin applied?")
    }

    @PublishedApi
    internal fun <Encoded, Decoded> exportAdapter(typeId: Int, adapter: KneeModule.Adapter<Encoded, Decoded>) {
        exportAdapters[typeId] = adapter
    }
}

open class KneeModule @PublishedApi internal constructor(
    private val registerNativeContainers: List<String>,
    private val registerNativeMethods: List<List<JniNativeMethod>>,
    private val preloadFqns: List<String>,
    private val exceptions: List<SerializableException>,
    private val dependencies: List<KneeModule>,
    block: (KneeModuleBuilder.() -> Unit)?
) {
    private val initializer: ((JniEnvironment) -> Unit)?
    private val exportAdapters: Map<Int, Adapter<*, *>>
    init {
        val builder = KneeModuleBuilder().apply { block?.invoke(this) }
        initializer = builder.initializer
        exportAdapters = builder.exportAdapters.toMap()
    }

    @Suppress("unused")
    constructor(vararg dependencies: KneeModule, block: (KneeModuleBuilder.() -> Unit)? = null) : this(
        registerNativeContainers = emptyList(),
        registerNativeMethods = emptyList(),
        preloadFqns = emptyList(),
        dependencies = dependencies.toList(),
        exceptions = emptyList(),
        block = block
    )

    // NOTE: could check types at runtime instead of UNCHECKED_CAST (pass them to Adapter constructor)
    @Suppress("UNCHECKED_CAST")
    internal fun <Encoded, Decoded> getExportAdapter(typeId: Int): Adapter<Encoded, Decoded> {
        val adapter = checkNotNull(exportAdapters[typeId]) { "No adapter for type: $typeId" }
        return adapter as Adapter<Encoded, Decoded>
    }

    internal fun collectExceptions(set: MutableSet<SerializableException>) {
        set.addAll(exceptions)
        dependencies.forEach { it.collectExceptions(set) }
    }

    private var initialized = atomic(0L)

    internal fun initializeIfNeeded(environment: JniEnvironment) {
        val id = environment.javaVM.rawValue.toLong()
        val oldId = initialized.getAndSet(id)
        if (id != oldId) {
            preloadFqns.forEach {
                ClassIds.get(environment, it)
            }
            registerNativeContainers.forEachIndexed { index, classFqn ->
                val methods = registerNativeMethods[index]
                environment.registerNatives(classFqn, *methods.toTypedArray())
            }
            initializer?.invoke(environment)
            dependencies.forEach {
                it.initializeIfNeeded(environment)
            }
        }
    }

    @PublishedApi
    internal class Adapter<Encoded, Decoded>(
        private val encoder: ((environment: JniEnvironment, decoded: Decoded) -> Encoded),
        private val decoder: ((environment: JniEnvironment, encoded: Encoded) -> Decoded)
    ) {
        fun encode(environment: JniEnvironment, decoded: Decoded): Encoded {
            return encoder(environment, decoded)
        }
        fun decode(environment: JniEnvironment, encoded: Encoded): Decoded {
            return decoder(environment, encoded)
        }
    }
}
