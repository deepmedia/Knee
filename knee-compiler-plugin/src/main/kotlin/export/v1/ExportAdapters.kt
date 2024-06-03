package io.deepmedia.tools.knee.plugin.compiler.export.v1

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.deepmedia.tools.knee.plugin.compiler.ClassCodec
import io.deepmedia.tools.knee.plugin.compiler.EnumCodec
import io.deepmedia.tools.knee.plugin.compiler.InterfaceCodec
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.import.concrete
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.utils.codegenFqName
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irReturnUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation

/**
 * "Adapter": read and write functions on both the native and the JVM side.
 * These can be later used at runtime by [ExportedCodec1].
 */
object ExportAdapters {

    fun exportIfNeeded(
        klass: IrClass,
        context: KneeContext,
        codegen: KneeCodegen,
        importInfo: ImportInfo?
    ) {
        val exportInfo = klass.exportInfo ?: return
        export(klass, exportInfo, context, codegen, importInfo)
    }

    fun export(
        klass: IrClass,
        exportInfo: ExportInfo,
        context: KneeContext,
        codegen: KneeCodegen,
        importInfo: ImportInfo?
    ) {
        val codec = context.mapper.get(klass.defaultType.concrete(importInfo))
        exportIr(klass, exportInfo.adapterNativeCoordinates, context, codec)
        exportCodegen(klass, exportInfo.adapterJvmCoordinates, context, codec, codegen)
    }

    private fun exportIr(klass: IrClass, location: ExportInfo.NativeCoordinates, context: KneeContext, codec: Codec) {
        val export = klass.functions.first { it.name == ExportInfo.DeclarationNames.AnnotatedFunction }
        export.body = DeclarationIrBuilder(context.plugin, export.symbol).irBlockBody { +irReturnUnit() }

        val spec: IrClass = when (location) {
            is ExportInfo.NativeCoordinates.InnerObject -> klass.declarations
                .filterIsInstance<IrClass>()
                .first { it.name == location.name }
        }

        val read = spec.functions.first { it.name.asString() == "read" }
        val write = spec.functions.first { it.name.asString() == "write" }

        read.body = DeclarationIrBuilder(context.plugin, read.symbol).irBlockBody {
            // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
            // TODO: reconsider this reverse flag as it does not generalize properly to export specs
            val codecContext =
                IrCodecContext(null, read.valueParameters[0], false, context.log)
            with(codec) {
                +irReturn(irDecode(codecContext, read.valueParameters[1]))
            }
        }

        write.body = DeclarationIrBuilder(context.plugin, write.symbol).irBlockBody {
            // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
            // We should reconsider this reverse flag as it does not generalize properly to export specs
            val codecContext =
                IrCodecContext(null, write.valueParameters[0], false, context.log)
            with(codec) {
                +irReturn(irEncode(codecContext, write.valueParameters[1]))
            }
        }
    }

    private fun exportCodegen(klass: IrClass, location: ExportInfo.JvmCoordinates, context: KneeContext, codec: Codec, codegen: KneeCodegen) {
        val (codegenContainer, codegenName) = when (location) {
            is ExportInfo.JvmCoordinates.InnerObject -> codegen.findExistingClass(name = klass.codegenFqName) to location.name
            is ExportInfo.JvmCoordinates.ExternalObject -> codegen.findExistingClass(name = location.parent) to location.name
        }
        checkNotNull(codegenContainer) {
            "Could not find codegen container for location: $location classFqName=${klass.codegenFqName}"
        }
        // Note: reverse = false but we don't relly know if the obj being converted is a param or return type
        // We should reconsider this reverse flag as it does not generalize properly to export specs
        val codecContext = CodegenCodecContext(null, false, context.log)

        codegenContainer.addChild(CodegenClass(TypeSpec.objectBuilder(codegenName.asString()).apply {
            addModifiers(KModifier.PUBLIC)
            val thisType = codegen.findExistingClass(name = klass.codegenFqName)!!.type.name //  klass.defaultType.concrete(importInfo).asTypeName()
            val jniType = when {
                klass.hasAnnotation(AnnotationIds.KneeClass) -> ClassCodec.encodedTypeForIr(context.symbols)
                klass.hasAnnotation(AnnotationIds.KneeEnum) -> EnumCodec.encodedTypeForIr(context.symbols)
                klass.hasAnnotation(AnnotationIds.KneeInterface) -> InterfaceCodec.encodedTypeForIr(context.symbols)
                else -> error("Exported class $klass is not enum nor interface nor class.")
            }.jvmOrNull!!.name
            funSpecs.add(
                FunSpec.builder("read")
                .addParameter("data", jniType, emptyList())
                .returns(thisType)
                .addCode(
                    CodeBlock.builder()
                    .apply { addStatement("return ${with(codec) { codegenDecode(codecContext, "data") }}") }
                    .build())
                .build())
            funSpecs.add(
                FunSpec.builder("write")
                .addParameter("data", thisType, emptyList())
                .returns(jniType)
                .addCode(
                    CodeBlock.builder()
                    .apply { addStatement("return ${with(codec) { codegenEncode(codecContext, "data") }}") }
                    .build())
                .build())
        }))
    }

}