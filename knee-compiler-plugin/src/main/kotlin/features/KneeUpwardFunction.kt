package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.functions.UpwardFunctionSignature
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.isOverridable
import org.jetbrains.kotlin.ir.util.parentAsClass

/**
 * source is an abstract function belonging to some interface.
 * The implementation will be added to some "Impl" class.
 */
class KneeUpwardFunction(
    source: IrSimpleFunction,
    parentInterface: KneeInterface?,
) : KneeFeature<IrSimpleFunction>(source, "Knee⬆") {

    /**
     * Read [UpwardFunctionSignature] for more info.
     */
    sealed class Kind {
        class InterfaceMember(val parent: KneeInterface) : Kind()

        val importInfo: ImportInfo? get() = when (this) {
            is InterfaceMember -> parent.importInfo
        }
    }

    val kind: Kind = Kind.InterfaceMember(parentInterface!!)

    init {
        source.requireNotComplex(this, allowSuspend = true)
        require(source.isOverridable) { "$this is not overridable." }
        require(source.parent is IrClass && source.parentAsClass.kind == ClassKind.INTERFACE) {
            "$this is not member of an interface."
        }
    }

    /**
     * The generated implementation. Might be set beforehand for example by reverse property handling.
     * If present, we shouldn't generate a new one of course.
     */
    var implementation: IrSimpleFunction? = null
}
