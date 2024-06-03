package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.KModifier
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenProperty
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeDownwardProperty
import io.deepmedia.tools.knee.plugin.compiler.import.concrete
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.utils.asModifier
import io.deepmedia.tools.knee.plugin.compiler.utils.asPropertySpec
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.types.IrSimpleType

fun processDownwardProperty(property: KneeDownwardProperty, context: KneeContext, codegen: KneeCodegen) {
    property.makeCodegen(codegen, context.symbols)
}

// Create the codegen property. If we don't do this, this would be done anyway
// by codegen.container() when invoked by the setter/getter function. But let's do
// it so we can add appropriate modifiers.
private fun KneeDownwardProperty.makeCodegen(codegen: KneeCodegen, symbols: KneeSymbols) {
    fun makeProperty(
        typeMapper: (IrSimpleType) -> IrSimpleType = { it },
        kdocSuffix: String = "",
        isOverride: Boolean = false
    ) = source.asPropertySpec(typeMapper).run {
        if (codegen.verbose) addKdoc("knee:properties${kdocSuffix}")
        addModifiers(source.visibility.asModifier())
        if (isOverride) addModifiers(KModifier.OVERRIDE)
        if (source.modality == Modality.OPEN) addModifiers(KModifier.OPEN)
        CodegenProperty(this).also {
            codegenProducts.add(it)
        }
    }

    // Where should the function implementation go?
     when (kind) {
         is KneeDownwardProperty.Kind.InterfaceMember -> {
            // For the abstract child, use addChildIfNeeded. This is because when user imports
            // a local type Flow<T> with more than implementation Flow<A>, Flow<B>, we make the codegen
            // as the generic Flow<T> and as such A.property and B.property should not write twice there.
            val abstract = makeProperty(kdocSuffix = ":abstract-interface-child")
            kind.owner.codegenClone?.addChildIfNeeded(abstract)

            val implementation = makeProperty(typeMapper = { it.concrete(kind.importInfo) })
            implementation.spec.addModifiers(KModifier.OVERRIDE)
            kind.owner.codegenImplementation.addChild(implementation)
            codegenImplementation = implementation
         }
         is KneeDownwardProperty.Kind.ClassMember -> {
             val isOverride = kind.owner.isOverrideInCodegen(symbols, this)
             val property = makeProperty(isOverride = isOverride)
             codegen.prepareContainer(source, kind.importInfo).addChild(property)
             codegenImplementation = property
         }
         is KneeDownwardProperty.Kind.TopLevel -> {
            val property = makeProperty()
            codegen.prepareContainer(source, kind.importInfo).addChild(property)
            codegenImplementation = property
         }
    }
}
