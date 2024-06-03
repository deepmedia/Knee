package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportAdapters
import io.deepmedia.tools.knee.plugin.compiler.features.KneeEnum
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeEnum
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeEnum
import io.deepmedia.tools.knee.plugin.compiler.utils.asModifier
import io.deepmedia.tools.knee.plugin.compiler.utils.isPartOf
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.types.KotlinType

fun processEnum(enum: KneeEnum, context: KneeContext, codegen: KneeCodegen) {
    if (enum.source.isPartOf(context.module)) {
        enum.makeCodegenClone(codegen)
    }

    val codec = EnumCodec(
        symbols = context.symbols,
        irType = enum.source.defaultType,
    )
    context.mapper.register(codec)

    if (!context.useExport2) {
        ExportAdapters.exportIfNeeded(enum.source, context, codegen, enum.importInfo)
    }
}

fun KneeEnum.makeCodegenClone(codegen: KneeCodegen) {
    val clone = TypeSpec.enumBuilder(source.name.asString()).run {
        addModifiers(source.visibility.asModifier())
        entries.forEach {
            addEnumConstant(it.name.asString())
        }
        CodegenClass(this)
    }
    codegen.prepareContainer(source, importInfo).addChild(clone)
    codegenProducts.add(clone)
}

class EnumCodec(
    symbols: KneeSymbols,
    irType: IrSimpleType,
) : Codec(irType, JniType.Int(symbols)) {

    companion object {
        fun encodedTypeForFir(module: ModuleDescriptor): KotlinType {
            return module.builtIns.intType
        }

        fun encodedTypeForIr(symbols: KneeSymbols): JniType {
            return JniType.Int(symbols)
        }
    }

    private val encode = symbols.functions(encodeEnum).single()
    private val decode = symbols.functions(decodeEnum).single()

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCall(decode).apply {
            putTypeArgument(0, localIrType)
            putValueArgument(0, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(encode).apply {
            putTypeArgument(0, localIrType)
            putValueArgument(0, irGet(local))
        }
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return "kotlin.enums.enumEntries<${localCodegenType.name}>()[$jni]"
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return "$local.ordinal"
    }
}