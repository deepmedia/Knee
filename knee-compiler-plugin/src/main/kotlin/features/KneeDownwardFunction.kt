package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.*

class KneeDownwardFunction(
    source: IrFunction,
    parentInstance: KneeFeature<*>?, // class or interface or object
    parentProperty: KneeDownwardProperty?
) : KneeFeature<IrFunction>(source, "Knee") {

    sealed class Kind(val property: KneeDownwardProperty?) {

        class TopLevel(property: KneeDownwardProperty?) : Kind(property)

        class ClassConstructor(val owner: KneeClass) : Kind(null)

        class ClassMember(val owner: KneeClass, property: KneeDownwardProperty?) : Kind(property)

        class InterfaceMember(val owner: KneeInterface, property: KneeDownwardProperty?) : Kind(property)

        class ObjectMember(val owner: KneeObject, property: KneeDownwardProperty?) : Kind(property)

        val importInfo: ImportInfo? get() = when (this) {
            is TopLevel -> null
            is ClassConstructor, is ClassMember -> null // TODO: why?
            is InterfaceMember -> owner.importInfo
            is ObjectMember -> owner.importInfo
        }
    }

    val kind: Kind = when {
        source.isTopLevel -> Kind.TopLevel(parentProperty)
        source is IrConstructor -> Kind.ClassConstructor(parentInstance as KneeClass)
        // source.name == referenceDisposerName() -> Kind.ClassDisposer
        source.dispatchReceiverParameter != null
                && source.parent is IrClass
                && source.parentAsClass.kind == ClassKind.CLASS -> {
            Kind.ClassMember(parentInstance as KneeClass, parentProperty)
        }
        source.dispatchReceiverParameter != null
                && source.parent is IrClass
                && source.parentAsClass.kind == ClassKind.INTERFACE -> {
            Kind.InterfaceMember(parentInstance as KneeInterface, parentProperty)
        }
        source.dispatchReceiverParameter != null
                && source.parent is IrClass
                && source.parentAsClass.kind == ClassKind.OBJECT -> {
            Kind.ObjectMember(parentInstance as KneeObject, parentProperty)
        }
        else -> error("$this must be top level, a class constructor, a class destructor or a class member.")
    }

    init {
        source.requireNotComplex(this, allowSuspend = true)
    }
}
