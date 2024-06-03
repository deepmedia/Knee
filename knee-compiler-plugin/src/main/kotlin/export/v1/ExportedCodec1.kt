package io.deepmedia.tools.knee.plugin.compiler.export.v1

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import io.deepmedia.tools.knee.plugin.compiler.ClassCodec
import io.deepmedia.tools.knee.plugin.compiler.EnumCodec
import io.deepmedia.tools.knee.plugin.compiler.InterfaceCodec
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.utils.canonicalName
import io.deepmedia.tools.knee.plugin.compiler.utils.codegenFqName
import io.deepmedia.tools.knee.plugin.compiler.utils.simple
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation

class ExportedCodec1(symbols: KneeSymbols, type: IrType, private val exportInfo: ExportInfo) : Codec(
    localType = type.simple("ExportedCodec1.init"),
    encodedType = when {
        type.classOrNull!!.owner.hasAnnotation(AnnotationIds.KneeEnum) -> EnumCodec.encodedTypeForIr(symbols)
        type.classOrNull!!.owner.hasAnnotation(AnnotationIds.KneeClass) -> ClassCodec.encodedTypeForIr(symbols)
        type.classOrNull!!.owner.hasAnnotation(AnnotationIds.KneeInterface) -> InterfaceCodec.encodedTypeForIr(symbols)
        else -> error("Should not happen: ${type.classFqName} not enum nor class nor interface.")
    }
) {

    private val irSpec: IrClass = run {
        val klass = type.classOrNull!!.owner
        val fqName = when (val location = exportInfo.adapterNativeCoordinates) {
            is ExportInfo.NativeCoordinates.InnerObject -> klass.classIdOrFail.createNestedClassId(location.name)
        }
        symbols.klass(fqName).owner
    }

    private val codegenSpec: TypeName = run {
        val klass = type.classOrNull!!.owner
        val fqName = when (val location = exportInfo.adapterJvmCoordinates) {
            is ExportInfo.JvmCoordinates.InnerObject -> klass.codegenFqName.child(location.name)
            is ExportInfo.JvmCoordinates.ExternalObject -> location.parent.child(location.name)
        }
        CodegenType.from(fqName).name
    }

    // We can't use %T format due to Codec interface design, and spec name can have a $ which must be
    // enclosed in backticks in order to compile.
    private val codegenSpecTypeString: String get() {
        return codegenSpec.canonicalName.split(".").joinToString(".") {
            if (it.contains("$")) "`$it`" else it
        }
    }

    override fun IrStatementsBuilder<*>.irDecode(
        irContext: IrCodecContext,
        jni: IrValueDeclaration
    ): IrExpression {
        return irCall(irSpec.functions.first { it.name.asString() == "read" }).apply {
            dispatchReceiver = irGetObject(irSpec.symbol)
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
        }
    }

    override fun IrStatementsBuilder<*>.irEncode(
        irContext: IrCodecContext,
        local: IrValueDeclaration
    ): IrExpression {
        return irCall(irSpec.functions.first { it.name.asString() == "write" }).apply {
            dispatchReceiver = irGetObject(irSpec.symbol)
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(local))
        }
    }

    override fun CodeBlock.Builder.codegenDecode(
        codegenContext: CodegenCodecContext,
        jni: String
    ): String {
        return "${codegenSpecTypeString}.read($jni)"
    }

    override fun CodeBlock.Builder.codegenEncode(
        codegenContext: CodegenCodecContext,
        local: String
    ): String {
        return "${codegenSpecTypeString}.write($local)"
    }
}