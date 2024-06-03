package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.context.KneeLogger
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType

abstract class Codec(
    val localIrType: IrSimpleType,
    val localCodegenType: CodegenType,
    val encodedType: JniType
) {

    // Most of the times the local types are identical so this constructor can be used.
    constructor(localType: IrSimpleType, encodedType: JniType) : this(localType, CodegenType.from(localType), encodedType)

    /**
     * Whether [irDecode] and [irEncode] should be called for backend side conversion.
     * By default, checks type equality but it is open to be overridden for special cases (e.g. one might
     * map Int to Int but still do something in between).
     */
    open val needsIrConversion: Boolean = encodedType !is JniType.Real || encodedType.kn != localIrType

    /**
     * Whether [codegenDecode] and [codegenEncode] should be called for frontend side conversion.
     * By default, checks type equality but it is open to be overridden for special cases (e.g. one might
     * map Int to Int but still do something in between).
     */
    open val needsCodegenConversion: Boolean = encodedType !is JniType.Real || encodedType.jvm != localCodegenType

    /**
     * Used before wrapping this codec in some other codec which needs encoded type of [JniType.Real].
     * Used for generics, nullable types and so on. This function exists in order to be overridden
     * by [ReturnVoidCodec], which should use [UnitCodec] when wrapped instead of itself.
     *
     * The alternative was to use UnitCodec by default but make sure that [ReturnVoidCodec] is used
     * in case of basic return types. That road is harder due to suspend support, not knowing if some type is
     * going to be used in both ways, ...
     */
    open fun wrappable(): Codec = this

    abstract fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression
    abstract fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression

    abstract fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String
    abstract fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String
}

data class IrCodecContext(
    val functionSymbol: IrFunctionSymbol?,
    val environment: IrValueDeclaration,
    // regular context decodes/reads the parameters  and encodes/writes the return type
    // reverse context decodes/reads the return type and encodes/writes the parameters
    val reverse: Boolean,
    val logger: KneeLogger
) {
    val encodesParameters get() = reverse
    val encodesReturn get() = !encodesParameters
    val decodesParameters get() = !encodesParameters
    val decodesReturn get() = !encodesReturn
}

data class CodegenCodecContext(
    val functionSymbol: IrFunctionSymbol?,
    val reverse: Boolean,
    val logger: KneeLogger
) {
    val encodesParameters get() = !reverse // T
    val encodesReturn get() = !encodesParameters // F
    val decodesParameters get() = !encodesParameters // F
    val decodesReturn get() = !encodesReturn // T
}