package io.deepmedia.tools.knee.plugin.compiler.services

import io.deepmedia.tools.knee.plugin.compiler.KneeIrGeneration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
class KneeComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        if (configuration[KneeCommandLineProcessor.KneeEnabled] == false) return
        val logs = configuration[CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY]!!
        val verboseLogs = configuration[KneeCommandLineProcessor.KneeVerboseLogs] ?: false
        val verboseRuntime = configuration[KneeCommandLineProcessor.KneeVerboseRuntime] ?: false
        val verboseCodegen = configuration[KneeCommandLineProcessor.KneeVerboseSources] ?: false
        val outputDir = File(configuration[KneeCommandLineProcessor.KneeOutputDir]!!)
        IrGenerationExtension.registerExtension(KneeIrGeneration(logs, verboseLogs, verboseRuntime, verboseCodegen, outputDir, true))
        // if (legacyIo) {
            // SyntheticResolveExtension.registerExtension(KneeSyntheticResolve())
        // }
    }

    override val supportsK2: Boolean
        get() = true
}