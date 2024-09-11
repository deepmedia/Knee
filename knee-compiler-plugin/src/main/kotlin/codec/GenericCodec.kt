package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeBoxed
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeBoxed
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression

/**
 * the "as any" codec - can wrap any other codec and is able to pass any value through JNI.
 * It does so by using [JniType.Object], regardless of the wrapped codec [JniType],
 * because it can transform between different jni types using a few tricks.
 *
 * It's very useful when type is not known as in generics - in many cases we want to
 * know the function signature so we need a fixed [JniType]. This is what this does.
 *
 * Note that this only works thanks to some inner codec passed to the constructor,
 * so the generic type is reified.
 */
class GenericCodec(
    private val symbols: KneeSymbols,
    innerCodec: Codec
) : Codec(
    localIrType = innerCodec.localIrType,
    localCodegenType = innerCodec.localCodegenType,
    encodedType = JniType.Object(symbols, CodegenType.from(ANY.copy(nullable = true)))
) {

    private val wrappedCodec: Codec = innerCodec.wrappable()
    private val wrappedType: JniType.Real = requireNotNull(wrappedCodec.encodedType as? JniType.Real) {
        "Wrapped codec doesn't use a real JniType."
    }

    /** Decoded value might be any JniType */
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        val data = when {
            !wrappedCodec.needsIrConversion -> irGet(local)
            else -> with(wrappedCodec) { irEncode(irContext, local) }
        }

        fun irEncodeBoxed(type: String) = irCall(symbols.functions(encodeBoxed(type)).single()).apply {
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, data)
        }

        return when (wrappedType) {
            is JniType.Object -> data // already a jobject
            is JniType.Long -> irEncodeBoxed("Long")
            is JniType.Int -> irEncodeBoxed("Int")
            is JniType.Double -> irEncodeBoxed("Double")
            is JniType.Float -> irEncodeBoxed("Float")
            is JniType.BooleanAsUByte -> irEncodeBoxed("Boolean")
            is JniType.Byte -> irEncodeBoxed("Byte")
        }
    }

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {

        fun irDecodeBoxed(type: String) = irCall(symbols.functions(decodeBoxed(type)).single()).apply {
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
        }

        val decoded = when (wrappedType) {
            is JniType.Object -> irGet(jni) // irAs(irGet(jni), wrappedType.kn)
            is JniType.Long -> irDecodeBoxed("Long")
            is JniType.Int -> irDecodeBoxed("Int")
            is JniType.Double -> irDecodeBoxed("Double")
            is JniType.Float -> irDecodeBoxed("Float")
            is JniType.BooleanAsUByte -> irDecodeBoxed("Boolean")
            is JniType.Byte -> irDecodeBoxed("Byte")
        }
        return when {
            !wrappedCodec.needsIrConversion -> decoded
            else -> with(wrappedCodec) { irDecode(irContext, irTemporary(decoded)) }
        }
    }

    /** Encoded value comes as Any, here we should basically cast to T. */
    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return if (wrappedCodec.needsCodegenConversion) {
            // TODO: "jni" might be an expression, not a variable. Can't use ${jni}_ safely.
            addStatement("val ${jni}_ = $jni as %T", wrappedCodec.encodedType.jvmOrNull!!.name)
            with(wrappedCodec) { codegenDecode(codegenContext, "${jni}_") }
        } else {
            "($jni) as ${wrappedCodec.encodedType.jvmOrNull!!.name}"
        }
    }

    /** Decoded value comes as T, which is already an instance of Any?. Nothing to do. */
    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return if (wrappedCodec.needsCodegenConversion) {
            with(wrappedCodec) { codegenEncode(codegenContext, local) }
        } else {
            local
        }
    }

    override fun toString(): String {
        return "GenericCodec($wrappedCodec)"
    }
}