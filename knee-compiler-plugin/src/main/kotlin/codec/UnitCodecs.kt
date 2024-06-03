package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.utils.irError
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType

fun unitCodecs(context: KneeContext) = listOf(
    // void type, only for return types
    ReturnVoidCodec(context.symbols),
    NothingCodec(context.symbols)
)


/**
 * As per [JniType.Void] definition, only return type is allowed.
 * This uses [Codec.wrappable] to return a codec that really encodes the unit type.
 */
class ReturnVoidCodec(symbols: KneeSymbols) : Codec(symbols.builtIns.unitType as IrSimpleType, JniType.Void) {

    private val companion = UnitCodec(symbols)

    override val needsCodegenConversion: Boolean = true // get a chance to throw
    override val needsIrConversion: Boolean = true // get a chance to throw
    private fun ensure(condition: Boolean) {
        check(condition) { "kotlin.Unit can't be used as a function parameter, only as return type." }
    }

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        ensure(irContext.decodesReturn)
        return irGet(jni)
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        ensure(irContext.encodesReturn)
        return irGet(local)
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        ensure(codegenContext.decodesReturn)
        return jni
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        ensure(codegenContext.encodesReturn)
        return local
    }

    override fun wrappable(): Codec = companion
}

class UnitCodec(private val symbols: KneeSymbols) : Codec(symbols.builtIns.unitType as IrSimpleType, JniType.Int(symbols)) {
    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irGetObject(symbols.builtIns.unitClass)
    }
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irInt(0)
    }
    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return "0"
    }
    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return "kotlin.Unit"
    }
}

class NothingCodec(private val symbols: KneeSymbols) : Codec(symbols.builtIns.nothingType as IrSimpleType, JniType.Int(symbols)) {
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irInt(0)
    }
    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return "0"
    }
    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irError(symbols, "kotlin.Nothing can't be decoded.")
    }
    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return "error(\"kotlin.Nothing can't be decoded.\")"
    }
}

