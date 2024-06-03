package io.deepmedia.tools.knee.plugin.compiler.symbols

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeAliasSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId


class KneeSymbols(private val plugin: IrPluginContext) {

    val builtIns: IrBuiltIns get() = plugin.irBuiltIns

    private val classes2 = mutableMapOf<ClassId, IrClassSymbol>()
    private val typeAliases2 = mutableMapOf<ClassId, IrTypeAliasSymbol>()
    private val functions2 = mutableMapOf<CallableId, Collection<IrSimpleFunctionSymbol>>()

    fun klass(classId: ClassId): IrClassSymbol = classes2.getOrPut(classId) {
        requireNotNull(plugin.referenceClass(classId)) { "Could not find classId $classId" }
    }

    fun functions(name: CallableId) = functions2.getOrPut(name) {
        plugin.referenceFunctions(name).also {
            require(it.isNotEmpty()) { "Could not find callableId $name" }
        }
    }

    fun typeAlias(name: ClassId) = typeAliases2.getOrPut(name) {
        requireNotNull(plugin.referenceTypeAlias(name)) { "Could not find type alias $name" }
    }


    fun typeAliasUnwrapped(name: ClassId): IrType = typeAlias(name).owner.expandedType
}