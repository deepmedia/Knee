package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.util.*

abstract class KneeFeature<Ir: IrDeclarationWithName>(
    val source: Ir,
    private val annotation: String,
) {

    var expectSources: List<IrDeclarationWithName> = emptyList()

    init {
        require(source.fileOrNull?.getPackageFragment()?.packageFqName?.isRoot != true) {
            "$this can't be in root package."
        }
        requireNotNull(source.fqNameWhenAvailable) {
            "$this must have a fully qualified name."
        }
        check(!source.isExpect) {
            "$this can't be an `expect` type."
        }
    }


    val irProducts = mutableListOf<IrDeclaration>()
    val codegenProducts = mutableListOf<CodegenDeclaration<*>>()

    fun dump(rawIr: Boolean = false): String {
        val hasProducts = irProducts.isNotEmpty() || codegenProducts.isNotEmpty()
        return if (!hasProducts) {
            if (rawIr) source.dump() else source.dumpKotlinLike()
        } else {
            val ir = irProducts.map { if (rawIr) it.dump() else it.dumpKotlinLike() }
            val codegen = codegenProducts.map { it.toString() }
            listOf("IR (${ir.size})", *ir.toTypedArray(), "CODEGEN (${codegen.size})", *codegen.toTypedArray())
                .joinToString(separator = "\n")
        }
    }

    final override fun toString() = "@$annotation " + (source.fqNameWhenAvailable?.asString() ?: source.name.asString())
}