package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.withNullability

/**
 * IR ENCODING: if incoming value is irNull(), don't go through super class. Return it as is.
 * IR DECODING: if incoming value is irNull(), don't go through super class. Return it as is.
 * CODEGEN ENCODING: if incoming value is "null", don't go through super class. Return it as is.
 * CODEGEN DECODING: if incoming value is "null", don't go through super class. Return it as is.
 *
 * Ideally we could just subclass GenericCodec but this codec definition must have the nulled types.
 */
class NullableCodec private constructor(
    localIrType: IrSimpleType,
    localCodegenType: CodegenType,
    private val genericCodec: GenericCodec,
    private val originalCodec: Codec
) : Codec(
    localIrType = localIrType,
    localCodegenType = localCodegenType,
    encodedType = genericCodec.encodedType
) {

    constructor(symbols: KneeSymbols, notNullCodec: Codec) : this(
        localIrType = notNullCodec.localIrType.withNullability(true),
        localCodegenType = notNullCodec.localCodegenType.name.copy(nullable = true).let { CodegenType.from(it) },
        genericCodec = GenericCodec(symbols, notNullCodec),
        originalCodec = notNullCodec
    )

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        if (!genericCodec.needsIrConversion) return irGet(jni)
        return irIfNull(
            type = localIrType,
            subject = irGet(jni),
            thenPart = irNull(),
            elsePart = irBlock { +with(genericCodec) { irDecode(irContext, jni) } }
        )
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        if (!genericCodec.needsIrConversion) return irGet(local)
        return irIfNull(
            type = encodedType.knOrNull!!,
            subject = irGet(local),
            thenPart = irNull(),
            elsePart = irBlock { +with(genericCodec) { irEncode(irContext, local) } }
        )
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        if (!genericCodec.needsCodegenConversion) {
            return jni
        }
        beginControlFlow("val ${jni}_: %T = if (($jni) == null) { $jni } else {", localCodegenType.name)
        add(buildCodeBlock {
            val processed = with(genericCodec) { codegenDecode(codegenContext, jni) }
            addStatement(processed)
        })
        endControlFlow()
        return "${jni}_"
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        if (!genericCodec.needsCodegenConversion) return local
        beginControlFlow("val ${local}_: %T = if (($local) == null) { $local } else {", encodedType.jvmOrNull!!.name)
        add(buildCodeBlock {
            val processed = with(genericCodec) { codegenEncode(codegenContext, local) }
            addStatement(processed)
        })
        endControlFlow()
        return "${local}_"
    }

    override fun toString(): String {
        return "$originalCodec?"
    }
}