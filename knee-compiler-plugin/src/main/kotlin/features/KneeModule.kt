package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.superClass

class KneeModule(source: IrClass) : KneeFeature<IrClass>(source, "KneeModule") {
    init {
        source.requireNotComplex(this, ClassKind.OBJECT)
        requireNotNull(source.superClass?.takeIf { it.classId == RuntimeIds.KneeModule }) {
            "$this must extend KneeModule."
        }
        require(source.visibility.isPublicAPI) { "$this must be a public, top-level object." }
        require(source.isTopLevel) { "$this must be a public, top-level object." }
    }
}