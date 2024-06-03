package io.deepmedia.tools.knee.plugin.compiler.context

import io.deepmedia.tools.knee.plugin.compiler.serialization.IrClassListSerializer
import io.deepmedia.tools.knee.plugin.compiler.serialization.IrClassSerializer
import io.deepmedia.tools.knee.plugin.compiler.serialization.IrSimpleTypeSerializer
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.file


object KneeOrigin {
    val KNEE by IrDeclarationOriginImpl.Synthetic
    val KNEE_IMPORT_PARENT by IrDeclarationOriginImpl.Synthetic
}

class KneeContext(
    val plugin: IrPluginContext,
    log: MessageCollector,
    verboseLogs: Boolean,
    verboseRuntime: Boolean,
    val module: IrModuleFragment,
    val useExport2: Boolean
) {

    val factory get() = plugin.irFactory

    val symbols = KneeSymbols(plugin)

    val json = Json {
        serializersModule = SerializersModule {
            contextual(IrClassSerializer(symbols))
            contextual(IrClassListSerializer(symbols))
            contextual(IrSimpleTypeSerializer(symbols))
        }
    }

    val mapper by lazy { KneeMapper(this, json) }

    val log = KneeLogger(log, verboseLogs, verboseRuntime)
}

