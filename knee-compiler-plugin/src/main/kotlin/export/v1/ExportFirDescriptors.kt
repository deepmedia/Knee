package io.deepmedia.tools.knee.plugin.compiler.export.v1

import io.deepmedia.tools.knee.plugin.compiler.ClassCodec
import io.deepmedia.tools.knee.plugin.compiler.EnumCodec
import io.deepmedia.tools.knee.plugin.compiler.InterfaceCodec
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.types.KotlinType

/**
 * Given a certain [ownerDescriptor] class to be exported, this is able to
 * create frontend (K1) declarations for:
 * 1. the native adapter class ([adapterDescriptor], [makeAdapterDescriptor])
 * 2. its inner read and write functions ([adapterFunctionNames], [makeAdapterFunctionDescriptor])
 * 3. the dummy, carrier, annotated function ([annotatedFunctionName], [makeAnnotatedFunctionDescriptor])
 *   This function is annotated with serialized version of [ExportInfo], so that consumers can read that.
 *
 * Note that 3. is fundamental for consumers to understand where the adapters are (on both native and JVM side).
 * This is done in frontend because IR is not allowed to add annotations.
 *
 * The first two steps (native adapter with read/write functions) are also needed for consumer modules
 * to be able to get the adapter class and its functions; if they wouldn't be written in FIR, they would
 * not appear at all to consumers after klib deserialization.
 *
 * (We may skip read/write here if we make the adapter extend some known, compiled interface)
 */
class ExportFirDescriptors(
    val ownerDescriptor: ClassDescriptor,
) {

    val exportInfo = ExportInfo(
        adapterNativeCoordinates = ExportInfo.NativeCoordinates.compute(ownerDescriptor),
        adapterJvmCoordinates = ExportInfo.JvmCoordinates.compute(ownerDescriptor),
    )

    val annotatedFunctionName: Name = ExportInfo.DeclarationNames.AnnotatedFunction

    fun makeAnnotatedFunctionDescriptor(): SimpleFunctionDescriptor {
        val serializedExportInfo = Json.encodeToString(exportInfo)

        val annotatedFunctionDescriptor = SimpleFunctionDescriptorImpl.create(
            ownerDescriptor,
            Annotations.create(listOf(
                AnnotationDescriptorImpl(
                    ownerDescriptor.module.findClassAcrossModuleDependencies(AnnotationIds.KneeMetadata)!!.defaultType,
                    mapOf(Name.identifier("metadata") to StringValue(serializedExportInfo)),
                    SourceElement.NO_SOURCE, // ownerDescriptor.source, // SourceElement.NO_SOURCE
                ),
            )),
            annotatedFunctionName,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            ownerDescriptor.source, // SourceElement.NO_SOURCE
        )
        annotatedFunctionDescriptor.initialize(
            null,
            ownerDescriptor.thisAsReceiverParameter, emptyList(), emptyList(), emptyList(),
            ownerDescriptor.builtIns.unitType, Modality.FINAL, DescriptorVisibilities.PUBLIC
        )
        return annotatedFunctionDescriptor
    }

    // For now always place the adapter as inner object, but there might be cases where this is not desirable
    // or not even possible (for example, imported stuff!). Need to check all edge cases
    val adapterFunctionNames = listOf(Name.identifier("read"), Name.identifier("write"))

    fun makeAdapterFunctionDescriptor(parent: ClassDescriptor, name: Name): SimpleFunctionDescriptor {
        val descriptor = SimpleFunctionDescriptorImpl.create(parent, Annotations.EMPTY, name, CallableMemberDescriptor.Kind.SYNTHESIZED, parent.source)
        // NOTE: this type is unsubstituted! If generics come into play, it should go through substitution
        val actualType = ownerDescriptor.defaultType
        val jniType = when {
            ownerDescriptor.annotations.hasAnnotation(AnnotationIds.KneeClass) -> ClassCodec.encodedTypeForFir(ownerDescriptor.module)
            ownerDescriptor.annotations.hasAnnotation(AnnotationIds.KneeEnum) -> EnumCodec.encodedTypeForFir(ownerDescriptor.module)
            ownerDescriptor.annotations.hasAnnotation(AnnotationIds.KneeInterface) -> InterfaceCodec.encodedTypeForFir(ownerDescriptor.module)
            else -> error("Exported owner $ownerDescriptor is not enum nor interface nor class.")
        }
        val isRead = name.asString() == "read"
        val returnType = when {
            isRead -> actualType
            else -> jniType
        }
        val inputType = when {
            isRead -> jniType
            else -> actualType
        }
        // Could use the JniEnvironment type alias, but whatever, it's still a pointer in the end.
        val envType = ownerDescriptor.module
            .findTypeAliasAcrossModuleDependencies(CInteropIds.COpaquePointer)!!
            .expandedType

        fun KotlinType.asValueParameter(name: String, index: Int): ValueParameterDescriptor {
            return ValueParameterDescriptorImpl(
                containingDeclaration = descriptor,
                original = null,
                index = index,
                annotations = Annotations.EMPTY,
                name = Name.identifier(name),
                outType = this,
                declaresDefaultValue = false,
                isCrossinline = false,
                isNoinline = false,
                varargElementType = null,
                source = parent.source,
            )
        }
        descriptor.initialize(null,
            parent.thisAsReceiverParameter, emptyList(), emptyList(),
            listOf(
                envType.asValueParameter("env", 0),
                inputType.asValueParameter("data", 1),
            ),
            returnType,
            Modality.FINAL, DescriptorVisibilities.PUBLIC
        )
        return descriptor
    }

    var adapterDescriptor: ClassDescriptor? = null
    private set

    fun makeAdapterDescriptor(ctx: LazyClassContext, dp: ClassMemberDeclarationProvider, name: Name): ClassDescriptor {
        val parent = requireNotNull(dp.correspondingClassOrObject) { "correspondingClassOrObject was null." }

        /* val scope = dp.ownerInfo?.let {
            ctx.declarationScopeProvider.getResolutionScopeForDeclaration(it.scopeAnchor)
        } ?: (ownerDescriptor as ClassDescriptorWithResolutionScopes).scopeForClassHeaderResolution */
        val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(dp.ownerInfo!!.scopeAnchor)

        // At first tried with ClassDescriptorImpl but it does not work, in that it does not trigger
        // the next round of getSyntheticFunctionNames for example.
        val objectDescriptor = SyntheticClassOrObjectDescriptor(
            c = ctx,
            parentClassOrObject = parent,
            containingDeclaration = ownerDescriptor,
            name = name,
            source = ownerDescriptor.source,
            outerScope = scope,
            modality = Modality.FINAL,
            visibility = DescriptorVisibilities.PUBLIC,
            annotations = Annotations.EMPTY,
            constructorVisibility = DescriptorVisibilities.PRIVATE,
            kind = ClassKind.OBJECT,
            isCompanionObject = false
        )
        objectDescriptor.initialize()
        return objectDescriptor.also {
            adapterDescriptor = it
        }
    }
}
