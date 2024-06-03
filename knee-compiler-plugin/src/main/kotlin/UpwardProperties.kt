package io.deepmedia.tools.knee.plugin.compiler

import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeUpwardProperty
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.name.Name

fun processUpwardProperty(property: KneeUpwardProperty, context: KneeContext) {
    property.makeIr(context)
}

private fun KneeUpwardProperty.makeIr(context: KneeContext): IrProperty {
    val implementationClass = kind.parent.irImplementation
    return implementationClass.addProperty {
        this.name = source.name
        this.origin = source.origin
        this.modality = Modality.FINAL
        this.isVar = source.isVar
    }.also { implementation ->
        implementation.overriddenSymbols += source.symbol
        val propertyType = getter.source.returnType
        // backing field: there's none, getter and setter delegate to JVM.
        // setter and getter: we add blank ones here, then body is added by ReverseFunctions.kt
        getter.let { knee ->
            knee.implementation = implementation.addGetter {
                this.returnType = propertyType
            }.apply {
                // Removing, handled in ReverseFunctions.kt
                // dispatchReceiverParameter = implementationClass.thisReceiver!!.copyTo(this)
            }
        }
        setter?.let { knee ->
            knee.implementation = implementation.factory.buildFun {
                this.returnType = context.symbols.builtIns.unitType
                this.name = Name.special("<set-${implementation.name}>")
            }.apply {
                implementation.setter = this
                correspondingPropertySymbol = implementation.symbol
                parent = implementation.parent
                // Removing, handled in ReverseFunctions.kt
                // dispatchReceiverParameter = implementationClass.thisReceiver!!.copyTo(this)
                // addValueParameter("value", propertyType)
            }
        }
    }.also {
        irProducts.add(it)
    }
}
