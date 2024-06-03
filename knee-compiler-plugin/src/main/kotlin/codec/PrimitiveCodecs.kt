package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeBoolean
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeBoolean
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType

fun primitiveCodecs(context: KneeContext) = listOf(
    // TODO: JVM shorts. Becomes jshort which is UChar in native.
    // TODO: JVM chars. Becomes jchar which is UShort in native.
    *IdentityCodec(JniType.Byte(context.symbols)).withCollectionCodecs(context),
    *IdentityCodec(JniType.Int(context.symbols)).withCollectionCodecs(context),
    *IdentityCodec(JniType.Long(context.symbols)).withCollectionCodecs(context),
    *IdentityCodec(JniType.Float(context.symbols)).withCollectionCodecs(context),
    *IdentityCodec(JniType.Double(context.symbols)).withCollectionCodecs(context),
    // JVM booleans. Becomes jboolean which is UByte in native.
    *BooleanCodec(context.symbols).withCollectionCodecs(context)
)

private class BooleanCodec(symbols: KneeSymbols) : Codec(symbols.builtIns.booleanType as IrSimpleType, JniType.BooleanAsUByte(symbols)) {
    private val create = symbols.functions(encodeBoolean).single()
    private val decode = symbols.functions(decodeBoolean).single()

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCall(decode).apply {
            putValueArgument(0, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(create).apply {
            putValueArgument(0, irGet(local))
        }
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String) = jni
    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String) = local

    override fun toString(): String {
        return "BooleanCodec"
    }
}