package io.deepmedia.tools.knee.plugin.compiler.import

import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.context.KneeOrigin
import io.deepmedia.tools.knee.plugin.compiler.utils.isPartOf
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*

fun IrSimpleType.concrete(importInfo: ImportInfo?): IrSimpleType = when (importInfo) {
    null -> this
    else -> importInfo.substitutor.substitute(this).let {
        checkNotNull(it as? IrSimpleType) { "Substitute of $this is not a simple type" }
    }
}

/**
 * Recreates the hierarchy of an imported declaration in the import location, up to the parent of this declaration.
 * All generated classes are marked as [KneeOrigin.KNEE_IMPORT_PARENT].
 */
fun IrDeclaration.writableParent(context: KneeContext, importInfo: ImportInfo?): IrDeclarationParent {
    if (isPartOf(context.module)) return parent
    requireNotNull(importInfo) {
        "Declaration $this is external but no ImportInfo provided."
    }

    var candidate: IrDeclarationContainer = importInfo.file
    val parentClasses = parents.takeWhile { it !is IrPackageFragment }.toList().reversed().toMutableList()

    // We could use deepCopy, but then it's pretty complex to reconcile different trees if writableParent is
    // called multiple times within the same tree.
    while (parentClasses.isNotEmpty()) {
        val next = parentClasses.removeFirst()
        require(next is IrClass) { "Declaration parent is not an IrClass, not sure what to do: $next" }
        var nextCopy = candidate.findDeclaration<IrClass> { it.name == next.name }
        if (nextCopy != null) {
            check(nextCopy.origin == KneeOrigin.KNEE_IMPORT_PARENT) {
                "Origin mismatch! Element: ${nextCopy!!.fqNameWhenAvailable} has: ${nextCopy!!.origin}"
            }
            candidate = nextCopy
        } else {
            nextCopy = context.factory.buildClass {
                modality = next.modality
                origin = KneeOrigin.KNEE_IMPORT_PARENT
                visibility = next.visibility
                name = next.name
            }.also { it.parent = candidate }
            candidate.addChild(nextCopy)
            candidate = nextCopy
        }
    }

    return candidate
}
