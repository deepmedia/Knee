package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.features.KneeClass
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportAdapters
import io.deepmedia.tools.knee.plugin.compiler.features.KneeObject
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addHandleConstructorAndField
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addObjectOverrides
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeClass
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeClass
import io.deepmedia.tools.knee.plugin.compiler.utils.asModifier
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeSpec
import io.deepmedia.tools.knee.plugin.compiler.utils.codegenFqName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.types.KotlinType

fun preprocessObject(klass: KneeObject, context: KneeContext) {
    context.mapper.register(ObjectCodec(
        symbols = context.symbols,
        irClass = klass.source,
    ))
}

fun processObject(klass: KneeObject, context: KneeContext, codegen: KneeCodegen) {
    klass.makeCodegen(codegen)
}

private fun KneeObject.makeCodegen(codegen: KneeCodegen) {
    val container = codegen.prepareContainer(source, importInfo)
    codegenClone = container.addChildIfNeeded(CodegenClass(source.asTypeSpec())).apply {
        if (codegen.verbose) spec.addKdoc("knee:objects")
        spec.addModifiers(source.visibility.asModifier())
        codegenProducts.add(this)
    }
}

class ObjectCodec(
    symbols: KneeSymbols,
    private val irClass: IrClass,
) : Codec(irClass.defaultType, JniType.Byte(symbols)) {

    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irByte(0)
    }

    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irGetObject(irClass.symbol)
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        return irClass.codegenFqName.asString()
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return "0"
    }
}