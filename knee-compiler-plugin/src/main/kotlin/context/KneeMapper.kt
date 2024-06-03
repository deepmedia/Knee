package io.deepmedia.tools.knee.plugin.compiler.context

import io.deepmedia.tools.knee.plugin.compiler.codec.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportedCodec1
import io.deepmedia.tools.knee.plugin.compiler.export.v1.exportInfo
import io.deepmedia.tools.knee.plugin.compiler.export.v2.ExportedCodec2
import io.deepmedia.tools.knee.plugin.compiler.export.v2.ExportedTypeInfo
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.metadata.ModuleMetadata
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeName
import io.deepmedia.tools.knee.plugin.compiler.utils.isPartOf
import io.deepmedia.tools.knee.plugin.compiler.utils.simple
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*

class KneeMapper(
    private val context: KneeContext,
    private val json: Json
) {
    private val symbols = context.symbols

    private val builtInCodecs = listOf(
        *unitCodecs(context).toTypedArray(),
        *primitiveCodecs(context).toTypedArray(),
        *unsignedCodecs(context.symbols).toTypedArray(),
        *stringCodecs(context).toTypedArray(),
        *bufferCodecs(context.symbols).toTypedArray(),
    )

    private val userDefinedCodecs = mutableListOf<Codec>()
    private val lazyCodecs = mutableListOf<Codec>()

    var dependencies: Map<IrClass, ModuleMetadata?> = emptyMap()

    fun register(vararg codecs: Codec) {
        this.userDefinedCodecs.addAll(codecs)
    }

    // This representation is the best one because it shows type parameters
    private val IrType.description get() = runCatching { this.simple("IrType.description").asTypeName() }.getOrElse { this }.toString()

    private val IrConstructorCall.description get() = "${type.description} ${valueArguments.map { it?.description }}"

    private val IrExpression.description get() = if (this is IrConst<*>) this.value.toString() else this.toString()

    private fun errorDescription(type: IrType): String {
        val klass = type.classOrNull?.owner
        if (klass != null
            && !klass.isPartOf(context.module)
            && listOf(AnnotationIds.KneeEnum, AnnotationIds.KneeClass, AnnotationIds.KneeInterface).any { klass.hasAnnotation(it) }) {
            return """
                Type ${type.description} cannot be passed through the JNI bridge in module ${context.module.name}. 
                It is a Knee type defined in an external module${klass.fileOrNull?.let { " " + it.module.name } ?: ""}. 
                To make it serializable here, use export<${type.description}>() in the original KneeModule configuration block.
            """.trimIndent()
        }

        return """
Type ${type.description} can not be passed through the JNI bridge.
Available:
${userDefinedCodecs.joinToString("\n") { "\t" + it.localIrType.description }}
Annotations:
${type.classOrNull?.owner?.annotations?.joinToString("\n") { "\t" + it.description  }}
""".trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    fun get(type: IrType, useSiteAnnotations: IrAnnotationContainer? = null): Codec {
        val raw = useSiteAnnotations?.getAnnotation(AnnotationIds.KneeRaw)
        if (raw != null) {
            val fqn = (raw.getValueArgument(0)!! as IrConst<String>).value
            val jobject = JniType.Object(context.symbols, CodegenType.from(fqn))
            require(jobject.kn.makeNullable() == type.makeNullable()) {
                "@KneeRaw(${fqn}) should be applied on a parameter of type 'jobject' or similar CPointer type alias."
            }
            return IdentityCodec(type = jobject)
        }
        return getConcrete(type)
    }

    private fun getConcrete(type: IrType): Codec {
        return requireNotNull(getConcreteOrNull(type)) { errorDescription(type) }
    }

    private fun getConcreteOrNull(type: IrType): Codec? {
        val candidate
            = userDefinedCodecs.firstOrNull { it.localIrType == type }
            ?: builtInCodecs.firstOrNull { it.localIrType == type }
            ?: lazyCodecs.firstOrNull { it.localIrType == type }
        if (candidate != null) return candidate

        if (type.isNullable()) {
            val notNull = getConcreteOrNull(type.makeNotNull())
            if (notNull != null) return NullableCodec(symbols, notNull)
        }

        val inner = CollectionKind.entries.firstNotNullOfOrNull { it.unwrapGeneric(type, symbols) }
        if (inner != null) {
            val wrappers = getConcreteOrNull(inner)?.collectionCodecs(context)
            if (wrappers != null) {
                return wrappers.first { it.localIrType == type }.also {
                    lazyCodecs.addAll(wrappers)
                }
            }
        }

        // export1
        val typeClass = type.classOrNull?.owner
        if (typeClass != null && !typeClass.isPartOf(context.module)) {
            val export1Info = typeClass.exportInfo
            if (export1Info != null) {
                return ExportedCodec1(symbols, type, export1Info).also {
                    lazyCodecs.add(it)
                }
            }
        }

        // export2
        // that is: see if any one of our dependency modules declared the capability to export this
        if (type is IrSimpleType) {
            val exportInfo = dependencies.findModuleExportingType(type)
            if (exportInfo != null) {
                return ExportedCodec2(symbols, exportInfo.first, exportInfo.second).also {
                    lazyCodecs.add(it)
                }
            }
        }

        return null
    }

    private fun Map<IrClass, ModuleMetadata?>.findModuleExportingType(type: IrSimpleType): Pair<IrClass, ExportedTypeInfo>? {
        return firstNotNullOfOrNull { (klass, metadata) ->
            if (metadata == null) return@firstNotNullOfOrNull null
            val exportedType = metadata.exportedTypes.firstOrNull { it.localIrType == type }
            if (exportedType != null) return klass to exportedType
            val dependencies = metadata.dependencyModules.associateWith { ModuleMetadata.read(it, json)!! }
            dependencies.findModuleExportingType(type)
        }
    }

}