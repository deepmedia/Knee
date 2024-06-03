package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenProperty
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.util.copyAnnotationsFrom

class KneeDownwardProperty(
    source: IrProperty,
    parentInstance: KneeFeature<*>?
) : KneeFeature<IrProperty>(source, "Knee") {

    sealed class Kind {
        class InterfaceMember(val owner: KneeInterface) : Kind()
        class ClassMember(val owner: KneeClass) : Kind()
        object TopLevel : Kind()

        val importInfo: ImportInfo? get() = when (this) {
            TopLevel -> null
            is ClassMember -> owner.importInfo
            is InterfaceMember -> owner.importInfo
        }
    }

    val kind = when (parentInstance) {
        is KneeInterface -> Kind.InterfaceMember(parentInstance)
        is KneeClass -> Kind.ClassMember(parentInstance)
        else -> Kind.TopLevel
    }


    val setter: KneeDownwardFunction? = source.setter?.let {
        it.copyAnnotationsFrom(source)
        KneeDownwardFunction(it, parentInstance, this)
    }

    val getter: KneeDownwardFunction = requireNotNull(source.getter) { "$this must have a getter." }.let {
        it.copyAnnotationsFrom(source)
        KneeDownwardFunction(it, parentInstance, this)
    }

    lateinit var codegenImplementation: CodegenProperty
}