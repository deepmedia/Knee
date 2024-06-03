package io.deepmedia.tools.knee.plugin.compiler.export.v2

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.withIndent
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.PlatformIds
import io.deepmedia.tools.knee.plugin.compiler.utils.irLambda
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors


object ExportAdapters2 {

    fun CodeBlock.Builder.codegenCreateExportAdapter(
        info: ExportedTypeInfo,
        context: KneeContext,
    ) {
        // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
        // We should reconsider this reverse flag as it does not generalize properly to export specs
        val codecContext = CodegenCodecContext(null, false, context.log)
        val codec = context.mapper.get(info.localIrType)

        addStatement("Adapter<%T, %T>(", info.encodedType.jvm.name, info.localCodegenType.name)
        withIndent {
            beginControlFlow("encoder =")
            addStatement(with(codec) { this@withIndent.codegenEncode(codecContext, "it") })
            endControlFlow()
            beginControlFlow(", decoder =")
            addStatement(with(codec) { this@withIndent.codegenDecode(codecContext, "it") })
            endControlFlow()
        }
        add(")")
    }

    fun DeclarationIrBuilder.irCreateExportAdapter(
        info: ExportedTypeInfo,
        context: KneeContext,
    ): IrConstructorCall {
        val adapterClass = context.symbols.klass(RuntimeIds.Adapter)
        val jniEnvironmentType = context.symbols.klass(CInteropIds.CPointer).typeWith(context.symbols.typeAliasUnwrapped(PlatformIds.JNIEnvVar))
        return irCallConstructor(adapterClass.constructors.single(), listOf(info.encodedType.kn, info.localIrType)).apply {
            // Encode
            putValueArgument(0, irLambda(
                context = context,
                parent = parent,
                valueParameters = listOf(jniEnvironmentType, info.localIrType),
                returnType = info.encodedType.kn,
                content = { lambda ->
                    // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
                    // TODO: reconsider this reverse flag as it does not generalize properly to export specs
                    val codecContext = IrCodecContext(null, lambda.valueParameters[0], false, context.log)
                    val codec = context.mapper.get(info.localIrType)
                    with(codec) { +irReturn(irEncode(codecContext, lambda.valueParameters[1])) }
                }
            ))
            // Decode
            putValueArgument(1, irLambda(
                context = context,
                parent = parent,
                valueParameters = listOf(jniEnvironmentType, info.encodedType.kn),
                returnType = info.localIrType,
                content = { lambda ->
                    // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
                    // TODO: reconsider this reverse flag as it does not generalize properly to export specs
                    val codecContext = IrCodecContext(null, lambda.valueParameters[0], false, context.log)
                    val codec = context.mapper.get(info.localIrType)
                    with(codec) { +irReturn(irDecode(codecContext, lambda.valueParameters[1])) }
                }
            ))
        }
    }
}