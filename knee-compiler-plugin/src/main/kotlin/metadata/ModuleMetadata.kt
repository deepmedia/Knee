package io.deepmedia.tools.knee.plugin.compiler.metadata

import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.export.v2.ExportedTypeInfo
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getAnnotation


@Serializable
data class ModuleMetadata private constructor(
    @Contextual private val dependencyModules_: List<IrClass_>,
    val exportedTypes: List<ExportedTypeInfo>,
) {

    constructor(
        exportedTypes: List<ExportedTypeInfo>,
        dependencyModules: List<IrClass>,
        nothing: Unit = Unit // fixes "same signature" with primary constructor
    ) : this(
        exportedTypes = exportedTypes,
        dependencyModules_ = dependencyModules.map { IrClass_(it) }
    )

    val dependencyModules: List<IrClass> get() = dependencyModules_.map { it.irClass }

    // Pointless wrapper to fix a K2 serialization error
    @Serializable
    private data class IrClass_(@Contextual val irClass: IrClass)

    companion object {
        fun read(module: IrClass, json: Json): ModuleMetadata? {
            @Suppress("UNCHECKED_CAST")
            val encoded = module.getAnnotation(AnnotationIds.KneeMetadata.asSingleFqName())
                ?.getValueArgument(0)
                ?.let { it as? IrConst<String> }
                ?.value ?: return null
            return json.decodeFromString<ModuleMetadata>(encoded)
        }
    }

    fun write(module: IrClass, context: KneeContext) {
        val existingMetadataAnnotation = module.getAnnotation(AnnotationIds.KneeMetadata.asSingleFqName())
        check(existingMetadataAnnotation == null) {
            "Module $module should not be annotated by @KneeMetadata(${existingMetadataAnnotation?.getValueArgument(0)?.dumpKotlinLike()})"
        }

        val metadataString = try {
            context.json.encodeToString(this)
        } catch (e: Throwable) {
            val canEncodeClassOnly = runCatching { context.json.encodeToString(IrClass_(module)) }
            throw RuntimeException("Failed to encode ModuleMetadata (canEncodeClassOnly? ${canEncodeClassOnly})", e)
        }

        context.plugin.metadataDeclarationRegistrar.addMetadataVisibleAnnotationsToElement(
            declaration = module,
            annotations = listOf(with(DeclarationIrBuilder(context.plugin, module.symbol)) {
                val metadataConstructor = context.symbols.klass(AnnotationIds.KneeMetadata).constructors.single()
                irCallConstructor(metadataConstructor, emptyList()).apply {
                    putValueArgument(0, irString(metadataString))
                }
            })
        )
    }
}