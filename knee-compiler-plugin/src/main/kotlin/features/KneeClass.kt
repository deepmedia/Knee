package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName

class KneeClass(
    source: IrClass,
    val importInfo: ImportInfo? = null
) : KneeFeature<IrClass>(source, "KneeClass") {

    val constructors: List<KneeDownwardFunction>
    val members: List<KneeDownwardFunction>
    val properties: List<KneeDownwardProperty>
    val isThrowable: Boolean

    init {
        source.requireNotComplex(
            this, ClassKind.CLASS,
            typeArguments = importInfo?.type?.arguments ?: emptyList()
        )

        val allConstructors = source.constructors.toList()
        val constructors = allConstructors
            .filter { it.hasAnnotation(AnnotationIds.Knee) }
            .takeIf { it.isNotEmpty() }
            ?: emptyList()
            // Removing this, people might want classes with no constructors exported.
            // ?: listOf(allConstructors.single { it.isPrimary })

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

        this.constructors = constructors.map { KneeDownwardFunction(it, parentInstance = this, parentProperty = null) }
        this.members = members.map { KneeDownwardFunction(it, parentInstance = this, parentProperty = null) }
        this.properties = properties.map { KneeDownwardProperty(it, parentInstance = this) }
        this.isThrowable = source.getAllSuperclasses().any {
            it.classId == KotlinIds.Throwable
        }
    }

    /**
     * A property should have the override modifier in codegen only if it refers to some superclass,
     * but the one and only superclass that we currently preserve in codegen is [kotlin.Throwable].
     */
    fun isOverrideInCodegen(symbols: KneeSymbols, property: KneeDownwardProperty): Boolean {
        if (!isThrowable) return false
        // NOTE: Symbol.equals() not good enough, need to compare by name
        // The overridden symbol may be a FAKE_OVERRIDE unlike the one we get directly from the class
        val throwableSymbols = symbols.klass(KotlinIds.Throwable).owner.properties.map { it.symbol.owner.name }.toList()
        val propertyOverriddenSymbols = property.source.overriddenSymbols.map { it.owner.name }
        return propertyOverriddenSymbols.any { it in throwableSymbols }
    }

    lateinit var codegenClone: CodegenClass

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T: IrOverridableDeclaration<*>> T.findAnnotatedParentRecursive(annotation: FqName): T? {
            return overriddenSymbols.asSequence().map {
                val t = it.owner as T
                if (t.hasAnnotation(annotation)) return@map t
                t.findAnnotatedParentRecursive(annotation)
            }.firstOrNull { it != null }
        }

        fun <T: IrOverridableDeclaration<*>> T.hasAnnotationCopyingFromParents(annotation: FqName): Boolean {
            if (hasAnnotation(annotation)) return true
            val parent = findAnnotatedParentRecursive(annotation) ?: return false
            copyAnnotationsFrom(parent)
            return true
        }
    }
}
