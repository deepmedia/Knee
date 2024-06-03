package io.deepmedia.tools.knee.plugin.compiler.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.import.writableParent
import io.deepmedia.tools.knee.plugin.compiler.utils.asPropertySpec
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeSpec
import io.deepmedia.tools.knee.plugin.compiler.utils.canonicalName
import org.jetbrains.kotlin.backend.jvm.ir.propertyIfAccessor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import java.io.File

class KneeCodegen(private val context: KneeContext, val root: File, val verbose: Boolean) {
    companion object {
        const val Filename = "Knee"
    }
    init {
        root.deleteRecursively()
        root.mkdirs()
    }

    private val files = mutableMapOf<String, CodegenFile>()

    private fun file(packageName: String) = files.getOrPut(packageName) {
        CodegenFile(FileSpec.builder(packageName, Filename))
    }

    fun findExistingClass(name: FqName): CodegenClass? {
        return files.values.asSequence()
            .flatMap { it.descendants }
            .filterIsInstance<CodegenClass>()
            .firstOrNull {
                it.type.name.canonicalName == name.asString()
            }
    }

    fun prepareContainer(
        declaration: IrDeclaration,
        importInfo: ImportInfo?,
        detectPropertyAccessors: Boolean = true,
        createCompanionObject: Boolean = false,
    ): CodegenDeclaration<*> {
        val irHierarchy: MutableList<IrDeclarationParent> = when (val container = declaration.writableParent(context, importInfo)) {
            is IrDeclaration -> container.parentsWithSelf.toMutableList()
            else -> mutableListOf(container)
        }

        // irHierarchy is a list which goes from the parent of declaration up until the file
        // [parentOfDeclaration, ... , ... , declarationFile]
        // We will then go from last to first and add all needed CodegenDeclarations
        var candidate: CodegenDeclaration<*> = file((irHierarchy.removeLast() as IrFile).packageFqName.asString())
        
        while (irHierarchy.isNotEmpty()) {
            val irParent = irHierarchy.removeLast()
            require(irParent is IrClass) { "Declaration parent is not an IrClass: $irParent (import=$importInfo all=${irHierarchy})" }
            val codegenParent = irParent.asTypeSpec()
            candidate = candidate.addChildIfNeeded(CodegenClass(codegenParent))
        }

        if (createCompanionObject && candidate is CodegenClass && !candidate.isCompanion) {
            candidate = candidate.addChildIfNeeded(CodegenClass(TypeSpec.companionObjectBuilder()))
        }

        if (detectPropertyAccessors && (declaration.isSetter || declaration.isGetter)) {
            // The parent of a setter/getter is actually the property.
            declaration as IrFunction
            val irProperty = declaration.propertyIfAccessor as IrProperty
            candidate = candidate.addChildIfNeeded(CodegenProperty(irProperty.asPropertySpec()))
        }
        return candidate
    }
    
    /* fun containerOf(
        declaration: IrDeclaration,
        importInfo: ImportInfo?,
        detectPropertyAccessors: Boolean = true,
        createCompanionObject: Boolean = false,
        replaceParentClassName: ((String) -> String)? = null,
    ): CodegenDeclaration<*> {
        val parents = declaration.parents.toList()
            .reversed()
            .dropWhile { it !is IrPackageFragment }
            .toMutableList()
        require(parents.removeFirstOrNull() is IrPackageFragment) { "First parent of $declaration is not IrPackageFragment, this is unexpected." }

        var candidate: CodegenDeclaration<*> = file(declaration.writableFile(context.module, importInfo).fqName.asString())

        while (parents.isNotEmpty()) {
            val irParent = parents.removeFirst()
            require(irParent is IrClass) { "Declaration parent is not an IrClass: $irParent" }
            val codegenParent = irParent.asTypeSpec(replaceParentClassName?.takeIf { parents.isEmpty() })
            candidate = candidate.maybePut(CodegenClass(codegenParent))
        }

        if (createCompanionObject && candidate is CodegenClass && !candidate.isCompanion) {
            candidate = candidate.maybePut(CodegenClass(TypeSpec.companionObjectBuilder()))
        }

        if (detectPropertyAccessors && (declaration.isSetter || declaration.isGetter)) {
            // The parent of a setter/getter is actually the property.
            declaration as IrFunction
            val irProperty = declaration.propertyIfAccessor as IrProperty
            candidate = candidate.maybePut(CodegenProperty(irProperty.asPropertySpec()))
        }
        return candidate
    } */

    fun write() {
        files.values.forEach { spec ->
            spec.prepare().build().writeTo(root)
        }
    }

    private fun CodegenDeclaration<*>.isProbablyPublic(): Boolean {
        return !modifiers.contains(KModifier.PRIVATE) && !modifiers.contains(KModifier.INTERNAL)
    }

    private fun <T: Any> CodegenDeclaration<T>.prepare(): T {
        val sorted = children.sortedByDescending { it.isProbablyPublic() }
        sorted.forEach {
            when (it) {
                is CodegenFile -> error("CodegenFile can't be a children of anything else.")
                is CodegenFunction -> {
                    val funSpec = it.prepare().build()
                    when (this) {
                        is CodegenFile -> spec.addFunction(funSpec)
                        is CodegenClass -> when {
                            it.isPrimaryConstructor -> spec.primaryConstructor(funSpec)
                            else -> spec.addFunction(funSpec) // works for regular constructors too
                        }
                        is CodegenProperty -> when {
                            it.isGetter -> spec.getter(funSpec)
                            it.isSetter -> spec.setter(funSpec)
                            else -> error("Can't add CodegenFunction to CodegenProperty, name = ${funSpec.name}")
                        }
                        is CodegenFunction -> error("Can't add CodegenFunction to CodegenFunction")
                    }
                }
                is CodegenClass -> {
                    val typeSpec = it.prepare().build()
                    when (this) {
                        is CodegenFile -> spec.addType(typeSpec)
                        is CodegenClass -> spec.addType(typeSpec)
                        is CodegenProperty -> error("Can't add CodegenType to CodegenProperty")
                        is CodegenFunction -> error("Can't add CodegenType to CodegenFunction")
                    }
                }
                is CodegenProperty -> {
                    val propertySpec = it.prepare().build()
                    when (this) {
                        is CodegenFile -> spec.addProperty(propertySpec)
                        is CodegenClass -> spec.addProperty(propertySpec)
                        is CodegenProperty -> error("Can't add CodegenProperty to CodegenProperty")
                        is CodegenFunction -> error("Can't add CodegenProperty to CodegenFunction")
                    }
                }
            }
        }
        return spec
    }
}