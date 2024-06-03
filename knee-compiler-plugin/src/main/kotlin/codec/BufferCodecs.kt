package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.CodeBlock
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.symbols.JDKIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.PlatformIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.PrimitiveBuffer
import io.deepmedia.tools.knee.plugin.compiler.utils.simple
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPropertyGetter


fun bufferCodecs(symbols: KneeSymbols) = listOf<Codec>(
    BufferCodec(symbols, "Byte"),
    BufferCodec(symbols, "Int"),
    BufferCodec(symbols, "Long"),
    BufferCodec(symbols, "Float"),
    BufferCodec(symbols, "Double")
)

private class BufferCodec(
    symbols: KneeSymbols,
    runtimeType: IrSimpleType,
    jdkType: CodegenType,
    private val dataType: String
): Codec(
    localIrType = runtimeType,
    localCodegenType = jdkType,
    encodedType = JniType.Object(symbols, jdkType)
) {

    override fun toString(): String {
        return "BufferCodec($dataType)"
    }

    private val objGetter = localIrType.classOrNull!!.getPropertyGetter("obj")!!
    private val createBuffer = localIrType.classOrNull!!.constructors.single {
        val params = it.owner.valueParameters
        params.size == 2 && params[1].type == symbols.typeAliasUnwrapped(PlatformIds.jobject)
    }

    constructor(symbols: KneeSymbols, dataType: String) : this(
        symbols = symbols,
        runtimeType = symbols.klass(PrimitiveBuffer(dataType)).defaultType.simple("BufferCodecs.init"),
        jdkType = CodegenType.from(JDKIds.NioBuffer(dataType)),
        dataType = dataType
    )

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCallConstructor(createBuffer, emptyList()).apply {
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(objGetter).apply { dispatchReceiver = irGet(local) }
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return local
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return jni
    }
}
