package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.features.KneeClass.Companion.hasAnnotationCopyingFromParents
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName

class KneeObject(
    source: IrClass,
    val importInfo: ImportInfo? = null,
) : KneeFeature<IrClass>(source, "KneeObject") {

    val members: List<KneeDownwardFunction>
    val properties: List<KneeDownwardProperty>

    init {
        source.requireNotComplex(
            this, ClassKind.OBJECT,
            typeArguments = importInfo?.type?.arguments ?: emptyList()
        )

        val members = source.functions
            .filter { it.hasAnnotationCopyingFromParents(AnnotationIds.Knee) }
            // exclude static function (see isStaticMethodOfClass impl)
            // and property accessors (one should use @Knee on the property instead)
            .filter { it.dispatchReceiverParameter != null }
            .filter { !it.isPropertyAccessor }
            .onEach { it.requireNotComplex("$this member ${it.name}", allowSuspend = true) }
            .toList()

        val properties = source.properties
            .filter { it.hasAnnotationCopyingFromParents(AnnotationIds.Knee) }
            .toList()

        this.members = members.map { KneeDownwardFunction(it, parentInstance = this, parentProperty = null) }
        this.properties = properties.map { KneeDownwardProperty(it, parentInstance = this) }
    }

    lateinit var codegenClone: CodegenClass
}
