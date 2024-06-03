package io.deepmedia.tools.knee.plugin.compiler

import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeCollector
import io.deepmedia.tools.knee.plugin.compiler.features.KneeFeature
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.classId
import java.io.File

class KneeIrGeneration(
    private val logs: MessageCollector,
    private val verboseLogs: Boolean,
    private val verboseRuntime: Boolean,
    private val verboseCodegen: Boolean,
    private val outputDir: File,
    private val useExport2: Boolean,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val context = KneeContext(pluginContext, logs, verboseLogs, verboseRuntime, moduleFragment, useExport2)
        val codegen = KneeCodegen(context, outputDir, verboseCodegen)
        process(context, codegen)
    }
}

private fun process(context: KneeContext, codegen: KneeCodegen) {
    val unit = "${context.module.name} (${context.module.descriptor.platform})"
    context.log.logMessage("[*] START unit: $unit")
    val data = KneeCollector(context.module)
    context.log.logMessage("[*] Collected")

    val hasData = data.hasDeclarations
    if (data.initializers.isEmpty() && data.modules.isEmpty()) {
        if (hasData) error("Compilation unit $unit should either initialize Knee  with `initKnee()` or create a KneeModule top-level property, exposed to dependent modules.")
        else return // all empty
    }
    if (data.initializers.isNotEmpty() && data.modules.isNotEmpty()) {
        error("Compilation unit $unit should either initialize Knee with `initKnee()` or create a KneeModule top-level property. Currently doing both.")
    }
    if (data.modules.size > 1) {
        context.log.logWarning("Compilation unit $unit has ${data.modules.size} modules: ${data.modules}")
    }
    context.log.logMessage("[*] Initializers: ${data.initializers.size} Modules: ${data.modules.size}")
    val initInfo = when {
        data.initializers.isNotEmpty() -> InitInfo.Initializer(data.initializers)
        data.modules.isNotEmpty() -> InitInfo.Module(data.modules)
        else -> error("Can't happen: ${data.initializers}, ${data.modules}")
    }
    context.log.logMessage("[*] Dependencies: ${context.mapper.dependencies.size} ${context.mapper.dependencies.map { it.key.classId }}")
    context.mapper.dependencies = initInfo.dependencies(context.json)

    // Moved export() to KneeModule so of course one can't ever export without a module
    /* if (context.useExport2 && !initInfo.canExport2Declarations) {
        val exported = (data.allClasses + data.allInterfaces + data.allEnums).filter { it.source.hasExportFlg }
        if (exported.isNotEmpty()) {
            error("Compilation unit $unit uses initKnee, not KneeModule(). As such, it can't export declarations " +
                    "to consumer libraries. Please remove the exported flag from ${exported.size} declarations: $exported")
        }
    }*/

    // Preprocessing round is meant for features to add codecs so that there can be circular dependencies between types
    context.log.logMessage("[*] Preprocessing target:${context.module.name} platform:${context.plugin.platform}")
    data.allInterfaces.processEach(context) { preprocessInterface(it, context) }
    data.allClasses.processEach(context) { preprocessClass(it, context) }

    context.log.logMessage("[*] Processing target:${context.module.name} platform:${context.plugin.platform}")
    data.allEnums.processEach(context) { processEnum(it, context, codegen) }
    data.allClasses.processEach(context) { processClass(it, context, codegen, initInfo) }
    data.allInterfaces.processEach(context) { processInterface(it, context, codegen, initInfo) }
    data.allUpwardProperties.processEach(context) { processUpwardProperty(it, context) }
    data.allDownwardProperties.processEach(context) { processDownwardProperty(it, context, codegen) }
    data.allUpwardFunctions.processEach(context) { processUpwardFunction(it, context, codegen) }
    data.allDownwardFunctions.processEach(context) { processDownwardFunction(it, context, codegen, initInfo) }

    processInit(info = initInfo, context = context, codegen = codegen)
    context.log.logMessage("[*] Writing generated code in ${codegen.root.absolutePath}")
    codegen.write()

    /* val exportedData = (data.allEnums + data.allInterfaces + data.allClasses).joinToString {
        it.source.defaultType.toString()
    }
    context.log.print("[*] Exporting data: $exportedData") */

}

private inline fun <T: KneeFeature<*>> List<T>.processEach(context: KneeContext, block: (T) -> Unit) {
    forEach { it.process(context, block) }
}

private inline fun <T: KneeFeature<*>> T.process(context: KneeContext, block: (T) -> Unit) {
    context.log.logMessage("[*] Processing $this:\n${this.dump(rawIr = false)}")
    block(this)
    context.log.logMessage("[*] Processed $this:\n${this.dump(rawIr = false)}")
}