package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.copyAnnotationsFrom

class KneeUpwardProperty(
    source: IrProperty,
    parentInterface: KneeInterface?
) : KneeFeature<IrProperty>(source, "Knee⬆") {

    sealed class Kind {
        class InterfaceMember(val parent: KneeInterface) : Kind()

        val importInfo: ImportInfo? get() = when (this) {
            is InterfaceMember -> parent.importInfo
        }
    }

    val kind = Kind.InterfaceMember(parentInterface!!)

    val setter: KneeUpwardFunction? = source.setter?.let {
        it.copyAnnotationsFrom(source)
        KneeUpwardFunction(it, parentInterface)
    }

    val getter: KneeUpwardFunction = requireNotNull(source.getter) { "$this must have a getter." }.let {
        it.copyAnnotationsFrom(source)
        KneeUpwardFunction(it, parentInterface)
    }
}