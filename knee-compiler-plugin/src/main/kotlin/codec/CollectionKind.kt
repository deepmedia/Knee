package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.utils.simple
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith

/**
 * Utility to define different kinds of collections and also to collectionify the
 * local types (both ir and codegen) for [CollectionCodec] implementation.
 */
enum class CollectionKind {
    Array, List, Set;

    fun unwrapGeneric(itemType: IrType, symbols: KneeSymbols): IrType? {
        // val generic = type.classOrNull?.owner?.defaultType ?: return null //  .toBuilder().apply { arguments = emptyList() }.buildSimpleType()
        val innerType = ((itemType as? IrSimpleType)?.arguments)?.singleOrNull()?.typeOrNull ?: return null
        return when {
            this == Array && itemType == symbols.builtIns.arrayClass.typeWith(innerType) -> innerType
            this == Set && itemType == symbols.builtIns.setClass.typeWith(innerType) -> innerType
            this == List && itemType == symbols.builtIns.listClass.typeWith(innerType) -> innerType
            else -> null
        }
    }

    fun getCollectionTypeOf(itemType: IrType, symbols: KneeSymbols): IrSimpleType {
        return when (this) {
            Array -> when (itemType) {
                symbols.builtIns.intType -> symbols.builtIns.intArray.defaultType
                symbols.builtIns.booleanType -> symbols.builtIns.booleanArray.defaultType
                symbols.builtIns.byteType -> symbols.builtIns.byteArray.defaultType
                symbols.builtIns.charType -> symbols.builtIns.charArray.defaultType
                symbols.builtIns.shortType -> symbols.builtIns.shortArray.defaultType
                symbols.builtIns.longType -> symbols.builtIns.longArray.defaultType
                symbols.builtIns.floatType -> symbols.builtIns.floatArray.defaultType
                symbols.builtIns.doubleType -> symbols.builtIns.doubleArray.defaultType
                else -> symbols.builtIns.arrayClass.typeWith(itemType)
            }
            Set -> symbols.builtIns.setClass.typeWith(itemType)
            List -> symbols.builtIns.listClass.typeWith(itemType)
        }.simple("CollectionKind.getCollectionTypeOf")
    }

    fun getCollectionTypeOf(itemType: CodegenType, symbols: KneeSymbols): CodegenType {
        if (itemType is CodegenType.IrBased) {
            return CodegenType.from(getCollectionTypeOf(itemType.irType, symbols))
        }
        val collectionType: TypeName = when (this) {
            Array -> when (itemType.name) {
                INT -> INT_ARRAY
                BOOLEAN -> BOOLEAN_ARRAY
                BYTE -> BYTE_ARRAY
                CHAR -> CHAR_ARRAY
                SHORT -> SHORT_ARRAY
                LONG -> LONG_ARRAY
                FLOAT -> FLOAT_ARRAY
                DOUBLE -> DOUBLE_ARRAY
                else -> ARRAY.parameterizedBy(itemType.name)
            }
            Set -> SET.parameterizedBy(itemType.name)
            List -> LIST.parameterizedBy(itemType.name)
        }
        return CodegenType.from(collectionType)
    }
}