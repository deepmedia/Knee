package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

class KneeEnum(
    source: IrClass,
    val importInfo: ImportInfo? = null
) : KneeFeature<IrClass>(source, "KneeEnum") {

    val entries: List<IrEnumEntry>

    init {
        source.requireNotComplex(this, ClassKind.ENUM_CLASS)
        val entries = mutableListOf<IrEnumEntry>()
        source.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) = Unit
            override fun visitEnumEntry(declaration: IrEnumEntry) {
                entries.add(declaration)
                super.visitEnumEntry(declaration)
            }
        })
        this.entries = entries
    }
}