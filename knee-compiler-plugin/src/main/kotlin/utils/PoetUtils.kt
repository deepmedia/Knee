package io.deepmedia.tools.knee.plugin.compiler.utils

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.types.Variance


fun IrClass.asTypeSpec(rename: ((String) -> String)? = null): TypeSpec.Builder {
    // NOTE: Could also add kmodifiers from visibility and modality
    val name = codegenName.map { rename?.invoke(it) ?: it }.asString()
    return when (kind) {
        ClassKind.ENUM_CLASS -> TypeSpec.enumBuilder(name)
        ClassKind.OBJECT -> TypeSpec.objectBuilder(name)
        ClassKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        ClassKind.ANNOTATION_CLASS -> TypeSpec.annotationBuilder(name)
        ClassKind.ENUM_ENTRY -> error("Enum entries ($this) can't become a TypeSpec.")
        ClassKind.CLASS -> TypeSpec.classBuilder(name)
    }
}

/**
 * Adding special logic for recursive types, as in:
 *    interface ClosedRange<T : Comparable<T>>
 * When calling Comparable<T>.asTypeName(), we handle Comparable easily, then find T and
 * start looking for T's bounds if any in its super types. But T's only super type
 * is set to Comparable<T> which means we will stack overflow.
 */
fun IrSimpleType.asTypeName(alreadyDescribedTypeParameters: MutableSet<IrTypeParameterSymbol> = mutableSetOf()): TypeName {
    // IrClassifierSymbol can be IrClassSymbol, IrScriptSymbol or IrTypeParameterSymbol

    return when (val s = classifier) {
        is IrClassSymbol -> {
            asClassTypeName(alreadyDescribedTypeParameters)
        }
        is IrTypeParameterSymbol -> TypeVariableName(
            name = s.owner.name.asString(),
            bounds = when {
                alreadyDescribedTypeParameters.add(s) -> emptyList()
                else -> s.owner.superTypes
                    .filterIsInstance<IrSimpleType>()
                    .map { it.asTypeName(alreadyDescribedTypeParameters) }
            }
        ).copy(nullable = nullability == SimpleTypeNullability.MARKED_NULLABLE)
        else -> error("Unexpected classifier: $s")
    }
}

private fun IrTypeArgument.asTypeName(alreadyDescribedTypeParameters: MutableSet<IrTypeParameterSymbol>): TypeName {
    return when (this) {
        is IrTypeProjection -> {
            val simpleType = checkNotNull(type as? IrSimpleType) { "IrTypeArgument.type not a simple type: $type" }
            val invariant = simpleType.asTypeName(alreadyDescribedTypeParameters)
            when (this.variance) {
                Variance.INVARIANT -> invariant
                Variance.IN_VARIANCE -> WildcardTypeName.consumerOf(inType = invariant)
                Variance.OUT_VARIANCE -> WildcardTypeName.producerOf(outType = invariant)
            }
        }
        is IrStarProjection -> STAR
        else -> error("Should not happen? ${this::class.simpleName}")
    }
}

private fun IrSimpleType.asClassTypeName(alreadyDescribedTypeParameters: MutableSet<IrTypeParameterSymbol>): TypeName {
    val fqName = requireNotNull(codegenClassFqName) {
        "IrType $this can't become a TypeName (classFqName is: ${classFqName}, classifier: ${classifier::class.simpleName})"
    }
    val className = ClassName.bestGuess(fqName.asString())
    return when (arguments.isEmpty()) {
        true -> className
        else -> className.parameterizedBy(arguments.map { it.asTypeName(alreadyDescribedTypeParameters) })
    }.copy(nullable = isNullable())
}


fun IrProperty.asPropertySpec(typeMapper: (IrSimpleType) -> IrSimpleType = { it }): PropertySpec.Builder {
    // NOTE: Could also add kmodifiers from visibility and modality
    val type = requireNotNull(backingField?.type ?: getter?.returnType)
    val simpleType = checkNotNull(type as? IrSimpleType) { "IrProperty.type not a simple type: $type" }
    val mappedType = typeMapper(simpleType)
    return PropertySpec.builder(codegenName.asString(), mappedType.asTypeName())
        .mutable(isVar)
}

fun DescriptorVisibility.asModifier(): KModifier {
    return when (delegate) {
        Visibilities.Public -> KModifier.PUBLIC
        Visibilities.Private -> KModifier.PRIVATE
        Visibilities.Protected -> KModifier.PROTECTED
        Visibilities.Local -> KModifier.PRIVATE
        Visibilities.Internal -> KModifier.INTERNAL
        Visibilities.InvisibleFake -> KModifier.PRIVATE
        Visibilities.PrivateToThis -> KModifier.PRIVATE
        Visibilities.Unknown -> KModifier.INTERNAL
        Visibilities.Inherited -> {
            // No idea whether this is correct
            return (this as? IrOverridableDeclaration<*>)
                ?.overriddenSymbols
                ?.map { it.owner }
                ?.filterIsInstance<IrDeclarationWithVisibility>()
                ?.firstOrNull { it.visibility.delegate != Visibilities.Inherited }
                ?.visibility
                ?.asModifier() ?: KModifier.PRIVATE
        }
        else -> KModifier.INTERNAL
    }
    // K1
    /* val effective = visibility.effectiveVisibility(descriptor = descriptor, checkPublishedApi = false)
    return when (visibility.delegate) {
        is EffectiveVisibility.Local,
        is EffectiveVisibility.PrivateInClass,
        is EffectiveVisibility.PrivateInFile -> KModifier.PRIVATE
        is EffectiveVisibility.InternalOrPackage -> KModifier.INTERNAL
        is EffectiveVisibility.Public -> KModifier.PUBLIC
        is EffectiveVisibility.Protected,
        is EffectiveVisibility.ProtectedBound,
        is EffectiveVisibility.InternalProtected,
        is EffectiveVisibility.InternalProtectedBound -> KModifier.PROTECTED
        is EffectiveVisibility.Unknown -> KModifier.PRIVATE // can't be inferred
    } */
}

val TypeName.simpleName: String get() {
    return when (this) {
        is ClassName -> simpleName
        is ParameterizedTypeName -> rawType.simpleName
        is Dynamic -> error("Not possible") // JS dynamic type
        is TypeVariableName -> error("Not possible") // describes generic named type parameter e.g. 'T : String'
        is LambdaTypeName -> error("Not possible") // describes lambdas
        is WildcardTypeName -> error("Not possible") // describes out String, in String, * ...
    }
}

val TypeName.canonicalName: String get() {
    return when (this) {
        is ClassName -> canonicalName
        is ParameterizedTypeName -> rawType.canonicalName
        is Dynamic, is LambdaTypeName, is TypeVariableName, is WildcardTypeName -> error("Not possible: ${this}")
    }
}

// Wrt canonicalName, this handles TypeVariableName.
// That can appear when creating codegen function for generic interfaces, because we use
// unsubstituted types in the base interface clone
val TypeName.disambiguationName: String get() {
    return when (this) {
        is ClassName -> canonicalName
        is ParameterizedTypeName -> rawType.canonicalName
        is TypeVariableName -> "${if (isReified) "reified " else ""}$variance $name : ${bounds.map { it.disambiguationName }}"
        is Dynamic, is LambdaTypeName, is WildcardTypeName -> error("Not possible: ${this}")
    }
}

/* val TypeName.packageName: String get() {
    return when (this) {
        is ClassName -> packageName
        is ParameterizedTypeName -> rawType.packageName
        is Dynamic, is LambdaTypeName, is TypeVariableName, is WildcardTypeName -> error("Not possible")
    }
} */

/* fun TypeName.copy(
    packageName: String = this.packageName,
    simpleName: String = this.simpleName
): TypeName {
    return when (this) {
        is ClassName -> {
            val simpleNames = simpleNames.toMutableList()
            simpleNames[simpleNames.lastIndex] = simpleName
            ClassName(packageName, simpleNames)
        }
        is ParameterizedTypeName -> (rawType.copy(packageName, simpleName) as ClassName).parameterizedBy(typeArguments)
        is Dynamic, is LambdaTypeName, is TypeVariableName, is WildcardTypeName -> error("Not possible")
    }
} */

fun TypeName.copy(
    clearGenerics: Boolean = false,
    wildcardGenerics: Boolean = false // useful for 'is' checks
): TypeName {
    return when (this) {
        is ClassName -> this
        is ParameterizedTypeName ->  when {
            clearGenerics -> rawType
            wildcardGenerics -> copy(typeArguments = List(typeArguments.size) { STAR })
            else -> this
        }
        is Dynamic, is LambdaTypeName, is TypeVariableName, is WildcardTypeName -> error("Not possible")
    }
}

// We only support const kinds.
fun IrValueParameter.defaultValueForCodegen(functionExpects: List<IrDeclarationWithName> = emptyList()): CodeBlock? {
    val expression = (defaultValueFromThisOrSupertypes ?: defaultValueFromExpect(functionExpects) ?: return null).expression
    if (expression is IrConst<*>) {
        return when (val kind = expression.kind) {
            is IrConstKind.Null -> CodeBlock.of("null")
            is IrConstKind.String -> CodeBlock.of("%S", kind.valueOf(expression))
            is IrConstKind.Float -> CodeBlock.of("%LF", kind.valueOf(expression))
            is IrConstKind.Long -> CodeBlock.of("%LL", kind.valueOf(expression))
            else -> CodeBlock.of("%L", kind.valueOf(expression))
            // is IrConstKind.Boolean -> CodeBlock.of(kind.valueOf(expression).toString())
            // is IrConstKind.Int -> CodeBlock.of(kind.valueOf(expression).toString())
            // is IrConstKind.Double -> CodeBlock.of(kind.valueOf(expression).toString())
            // else -> return null
        }
    } else if (expression is IrGetEnumValue && type is IrSimpleType) {
        // No need to check whether the type is serializable, that would throw an error somewhere else
        val type: TypeName = (type as IrSimpleType).asTypeName()
        val entry: String = expression.symbol.owner.name.asString()
        return CodeBlock.of("%T.%N", type, entry)
    }
    return null
}

private val IrValueParameter.defaultValueFromThisOrSupertypes: IrExpressionBody? get() {
    if (defaultValue != null) return defaultValue
    val parent = parent as? IrSimpleFunction ?: return null
    return parent.overriddenSymbols.asSequence()
        .map { it.owner }
        .mapNotNull { it.valueParameters.firstOrNull { it.name == name } }
        .firstNotNullOfOrNull { it.defaultValueFromThisOrSupertypes }
}


private fun IrValueParameter.defaultValueFromExpect(functionExpects: List<IrDeclarationWithName>): IrExpressionBody? {
    return functionExpects.asSequence()
        .filterIsInstance<IrSimpleFunction>()
        .mapNotNull { it.valueParameters.firstOrNull { it.name == name } }
        .firstNotNullOfOrNull { it.defaultValueFromThisOrSupertypes }
}

