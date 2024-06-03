package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.STRING
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeString
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeString
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType

fun stringCodecs(context: KneeContext): List<Codec> = listOf(
    StringCodec(context.symbols) // .withCollectionCodecs(context)
)

private class StringCodec(symbols: KneeSymbols) : Codec(
    localType = symbols.builtIns.stringType as IrSimpleType,
    encodedType = JniType.Object(symbols, CodegenType.from(STRING))
) {
    private val encode = symbols.functions(encodeString).single()
    private val decode = symbols.functions(decodeString).single()

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCall(decode).apply {
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(encode).apply {
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(local))
        }
    }

    // In codegen / JVM world, a String is already a String - nothing to do

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return jni
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return local
    }
}