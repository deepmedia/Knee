package io.deepmedia.tools.knee.plugin.compiler.export.v1

import io.deepmedia.tools.knee.plugin.compiler.instances.InterfaceNames.asInterfaceName
import io.deepmedia.tools.knee.plugin.compiler.serialization.FqNameSerializer
import io.deepmedia.tools.knee.plugin.compiler.serialization.NameSerializer
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.functions

/**
 * For owned declarations, here we are reading in IR the information that was written in the frontend
 * descriptor-based step.
 * For external declarations, we are reading information provided by their own compiler invocation.
 */
@Suppress("UNCHECKED_CAST")
val IrClass.exportInfo: ExportInfo? get() {
    if (!hasExport1Flag) return null
    // Reading in backend IR the information we wrote in frontend descriptor step...
    return functions
        .first { it.name == ExportInfo.DeclarationNames.AnnotatedFunction }
        .annotations
        .single()
        .valueArguments[0]
        .let { it as IrConst<String> }
        .value
        .let { Json.decodeFromString(it) }
}

@Serializable
data class ExportInfo(
    val adapterNativeCoordinates: NativeCoordinates,
    val adapterJvmCoordinates: JvmCoordinates
) {

    object DeclarationNames {
        /** A dummy function, added to the class to be exported, that will carry the annotation with serialized [ExportInfo] */
        val AnnotatedFunction = Name.identifier("Knee\$ExportInfoHandle")
        /**
         * Name of the read/write adapter, used for both native and JVM side (with some exceptions!)
         * It doesn't matter though because it's already exposed in the location objects and that's where it should be read.
         */
        val SyntheticAdapter = Name.identifier("Knee\$ExportAdapter")
    }

    /**
     * Location of the native side of the adapter.
     */
    @Serializable
    sealed class NativeCoordinates {
        @Serializable
        data class InnerObject(@Serializable(with = NameSerializer::class) val name: Name) : NativeCoordinates()

        companion object {
            /**
             * For now, all IR specs are written as an inner object named [DeclarationNames.SyntheticAdapter].
             * Soon we might need other options because this is not always desireable or even possible.
             */
            fun compute(descriptor: ClassDescriptor): NativeCoordinates {
                return InnerObject(DeclarationNames.SyntheticAdapter)
            }
        }
    }

    @Serializable
    sealed class JvmCoordinates {
        @Serializable
        data class InnerObject(@Serializable(with = NameSerializer::class) val name: Name) : JvmCoordinates()

        @Serializable
        data class ExternalObject(
            @Serializable(with = FqNameSerializer::class) val parent: FqName,
            @Serializable(with = NameSerializer::class) val name: Name
        ) : JvmCoordinates()

        companion object {
            fun compute(descriptor: ClassDescriptor): JvmCoordinates {
                return when {
                    descriptor.annotations.hasAnnotation(AnnotationIds.KneeClass) -> InnerObject(DeclarationNames.SyntheticAdapter)
                    descriptor.annotations.hasAnnotation(AnnotationIds.KneeEnum) -> InnerObject(DeclarationNames.SyntheticAdapter)
                    descriptor.annotations.hasAnnotation(AnnotationIds.KneeInterface) -> {
                        // Inner object of the impl class.
                        // We can use a nicer name since the spec is not a member of the user-facing class
                        val parentsName = descriptor.codegenFqName.parent()
                        val implName = descriptor.codegenName.asInterfaceName(null)
                        val mergedName = FqName("$parentsName.$implName")
                        ExternalObject(mergedName, Name.identifier("ExportSpec"))
                    }
                    else -> error("Exported owner $descriptor is not enum nor interface nor class.")
                }
            }
        }
    }
}

