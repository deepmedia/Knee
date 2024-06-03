package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

private class UnsignedCodec(
    symbols: KneeSymbols,
    signed: JniType.Real,
    unsignedClass: ClassId,
    toUnsignedFunctions: CallableId,
    toSignedFunction: String
): Codec(
    localType = symbols.klass(unsignedClass).typeWith(),
    encodedType = signed
) {

    private val description = "UnsignedCodec(${unsignedClass})"

    override fun toString(): String = description

    private val toUnsigned = symbols.functions(toUnsignedFunctions).single {
        it.owner.extensionReceiverParameter?.type == signed.kn
    }
    private val toSigned = symbols.klass(unsignedClass).functionByName(toSignedFunction)

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCall(toUnsigned).apply { extensionReceiver = irGet(jni) }
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(toSigned).apply { dispatchReceiver = irGet(local) }
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        codegenContext.logger.injectLog(this, "$description ENCODING")
        return "$local.${toSigned.owner.name.asString()}()" // uint.toInt()
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        codegenContext.logger.injectLog(this, "$description DECODING")
        return "$jni.${toUnsigned.owner.name.asString()}()" // int.toUInt()
    }
}

private fun UInt(symbols: KneeSymbols) = UnsignedCodec(symbols, JniType.Int(symbols), KotlinIds.UInt, KotlinIds.toUInt, "toInt")
private fun ULong(symbols: KneeSymbols) = UnsignedCodec(symbols, JniType.Long(symbols), KotlinIds.ULong, KotlinIds.toULong, "toLong")
private fun UByte(symbols: KneeSymbols) = UnsignedCodec(symbols, JniType.Byte(symbols), KotlinIds.UByte, KotlinIds.toUByte, "toByte")

fun unsignedCodecs(symbols: KneeSymbols) = listOf<Codec>(
    UInt(symbols),
    ULong(symbols),
    UByte(symbols),
    // TODO: UShort
    // TODO: UChar
    // TODO: wrap in collections
)