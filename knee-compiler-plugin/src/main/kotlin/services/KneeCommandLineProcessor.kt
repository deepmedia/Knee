package io.deepmedia.tools.knee.plugin.compiler.services

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

// https://github.com/ZacSweers/redacted-compiler-plugin/blob/main/redacted-compiler-plugin/src/main/kotlin/dev/zacsweers/redacted/compiler/RedactedCommandLineProcessor.kt
@OptIn(ExperimentalCompilerApi::class)
class KneeCommandLineProcessor : CommandLineProcessor {
    companion object {
        val KneeEnabled = CompilerConfigurationKey<Boolean>("enabled")
        val KneeOutputDir = CompilerConfigurationKey<String>("outputDir")

        val KneeVerboseLogs = CompilerConfigurationKey<Boolean>("verboseLogs")
        val KneeVerboseRuntime = CompilerConfigurationKey<Boolean>("verboseRuntime")
        val KneeVerboseSources = CompilerConfigurationKey<Boolean>("verboseSources")
        // val KneeLegacyIo = CompilerConfigurationKey<Boolean>("legacyImportExport")
    }

    override val pluginId: String = "knee-compiler-plugin"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption("enabled", "<true | false>","Whether knee processing is enabled.", required = true),
        CliOption("verboseLogs", "<true | false>","Enable or disable plugin logs.", required = false),
        CliOption("verboseRuntime", "<true | false>","Enable or disable runtime logs.", required = false),
        CliOption("verboseSources", "<true | false>","Enable or disable JVM sources comments.", required = false),
        CliOption("outputDir", "<String>","Absolute path to the generated source code directory.", required = false),
        // CliOption("legacyImportExport", "<true | false>","Whether to use the legacy (K1 only) export/import logic.", required = false),
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "enabled" -> configuration.put(KneeEnabled, value.toBoolean())
            "verboseLogs" -> configuration.put(KneeVerboseLogs, value.toBoolean())
            "verboseRuntime" -> configuration.put(KneeVerboseRuntime, value.toBoolean())
            "verboseSources" -> configuration.put(KneeVerboseSources, value.toBoolean())
            "outputDir" -> configuration.put(KneeOutputDir, value)
            // "legacyImportExport" -> configuration.put(KneeLegacyIo, value.toBoolean())
        }
    }
}