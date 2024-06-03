package io.deepmedia.tools.knee.plugin.compiler.export.v2

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.defaultType

class ExportedCodec2(symbols: KneeSymbols, exportingModule: IrClass, exportedType: ExportedTypeInfo) : Codec(
    localIrType = exportedType.localIrType,
    localCodegenType = exportedType.localCodegenType,
    encodedType = exportedType.encodedType,
) {

    private val typeId = exportedType.id
    private val moduleObject = exportingModule
    private val getAdapterFunction = symbols.functions(RuntimeIds.KneeModule_getExportAdapter).single()
    private val adapterDecodeFunction = symbols.functions(RuntimeIds.Adapter_decode).single()
    private val adapterEncodeFunction = symbols.functions(RuntimeIds.Adapter_encode).single()

    private fun IrStatementsBuilder<*>.irGetAdapter(): IrExpression {
        return irCall(getAdapterFunction).apply {
            dispatchReceiver = irGetObject(moduleObject.symbol)
            putTypeArgument(0, encodedType.knOrNull!!)
            putTypeArgument(1, localIrType)
            putValueArgument(0, irInt(typeId))
        }
    }

    override fun IrStatementsBuilder<*>.irDecode(
        irContext: IrCodecContext,
        jni: IrValueDeclaration
    ): IrExpression {
        return irCall(adapterDecodeFunction).apply {
            dispatchReceiver = irGetAdapter()
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(
        irContext: IrCodecContext,
        local: IrValueDeclaration
    ): IrExpression {
        return irCall(adapterEncodeFunction).apply {
            dispatchReceiver = irGetAdapter()
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(local))
        }
    }

    private fun CodeBlock.Builder.addGetAdapterStatement(variableName: String) {
        val module = moduleObject.defaultType.asTypeName()
        addStatement("val $variableName = %T.getExportAdapter<%T, %T>($typeId)", module, encodedType.jvmOrNull!!.name, localCodegenType.name)
    }

    override fun CodeBlock.Builder.codegenDecode(
        codegenContext: CodegenCodecContext,
        jni: String
    ): String {
        addGetAdapterStatement("adapter_")
        return "adapter_.decode($jni)"
    }

    override fun CodeBlock.Builder.codegenEncode(
        codegenContext: CodegenCodecContext,
        local: String
    ): String {
        addGetAdapterStatement("adapter_")
        return "adapter_.encode($local)"
    }
}