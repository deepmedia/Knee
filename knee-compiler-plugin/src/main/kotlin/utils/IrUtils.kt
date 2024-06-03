package io.deepmedia.tools.knee.plugin.compiler.utils

import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.context.KneeOrigin
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

fun IrType.simple(info: String): IrSimpleType {
    return checkNotNull(this as? IrSimpleType) { "$info: type $this is not IrSimpleType."}
}

fun IrDeclaration.isPartOf(module: IrModuleFragment): Boolean {
    return runCatching { file.module == module }.getOrElse { false }
}

fun IrFunction.requireNotComplex(description: Any, allowSuspend: Boolean = false) {
    require(typeParameters.isEmpty()) { "$description can't have type parameters." }
    require(extensionReceiverParameter == null) { "$description can't be an extension function." }
    require(contextReceiverParametersCount == 0) { "$description can't have context receivers." }
    require(allowSuspend || !isSuspend) { "$description can't be suspend." }
    require(!isExpect) { "$description can't be an expect function, please annotate the actual function instead." }
}

fun IrClass.requireNotComplex(
    description: Any,
    kind: ClassKind?,
    // allowNested: Boolean = true,
    typeArguments: List<IrTypeArgument> = emptyList()
) {
    if (typeParameters.isNotEmpty()) {
        require(typeArguments.size == typeParameters.size) { "$description can't have unmatched type parameters." }
        typeArguments.forEach {
            when (it) {
                is IrTypeProjection -> {
                    require(!it.type.isTypeParameter()) { "Type parameter ${it.type} of $description can't be a generic type coming from some parent declaration." }
                    require(it.variance == Variance.INVARIANT) { "Type parameter ${it.type} of $description must be invariant, was ${it.variance}." }
                }
                is IrStarProjection -> error("Type parameter $it can't be a wildcard.")
                else -> error("Should not happen? ${it::class.simpleName}")
            }
        }
    }
    require(!isInner) { "$description can't be an inner class." }
    require(kind == null || this.kind == kind) { "$description must be a $kind (was ${this.kind})." }
    if (kind != null && kind != ClassKind.INTERFACE) {
        require(modality != Modality.ABSTRACT) { "$description can't be an abstract class" }
    }
    // require(allowNested || parentClassOrNull == null) { "$this can't be contained in another class." }
    require(!isExpect) { "$description can't be an expect class, please annotate the actual class instead." }
}

/**
 * Writing properties is tricky.
 * A simple property here is a 'val' with default getter and some initializer.
 */
fun IrDeclarationContainer.addSimpleProperty(
    plugin: IrPluginContext,
    type: IrType,
    name: Name,
    initializer: IrBuilderWithScope.() -> IrExpression
): IrProperty {
    val parent = this
    val property = plugin.irFactory.buildProperty {
        isVar = false
        origin = KneeOrigin.KNEE
        this.name = name
    }
    property.apply {
        this.parent = parent
        backingField = factory.buildField {
            isStatic = parent is IrFile || (parent is IrDeclaration && parent.isFileClass) // very important
            origin = IrDeclarationOrigin.PROPERTY_BACKING_FIELD
            this.name = name
            this.type = type
        }.apply {
            correspondingPropertySymbol = property.symbol
            this.parent = parent
            this.initializer = DeclarationIrBuilder(plugin, symbol).run {
                irExprBody(initializer())
            }
        }
        addGetter {
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
            returnType = backingField!!.type
        }.apply {
            body = DeclarationIrBuilder(plugin, symbol).irBlockBody {
                +irReturn(irGetField(null, backingField!!))
            }
        }
    }
    declarations.add(property)
    return property
}

/**
 * This is one of the two ways of passing a lambda in IR code.
 * It's equivalent to, for example, list.map { ... }. We proceed by creating a lambda whose parent is inferred
 * from the current scope, typically it is the parent function. Note that as far as I can see the lambda itself is
 * not added as a child of the parent, so the wiring only goes in one direction.
 *
 * Lambda should have the [IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA] origin
 * and [DescriptorVisibilities.LOCAL] visibility.
 *
 * Then we create a [IrFunctionExpression] out of it, which makes it a lambda.
 * Something similar is done in CStructVarClassGenerator.kt in kotlin source code (1.7.0? 1.6.21?).
 * https://github.com/JetBrains/kotlin/blob/9148094bbdc53d4c5cfb16bebab41bc5f561e19a/[…]in/backend/konan/ir/interop/cstruct/CStructVarClassGenerator.kt
 * Sample IR code:
 *
 * fun functionAcceptingALambda(lambdaArgument: (Int) -> Int) = ...
 * fun main() {
 *     functionAcceptingALambda { it + 1 }
 * }
 *
 * lambdaArgument: FUN_EXPR type=kotlin.Function1<kotlin.Int, kotlin.Int> origin=LAMBDA
 *   FUN LOCAL_FUNCTION_FOR_LAMBDA name:<anonymous> visibility:local modality:FINAL <> (it:kotlin.Int) returnType:kotlin.Int
 *     VALUE_PARAMETER name:it index:0 type:kotlin.Int
 *       BLOCK_BODY
 *         ... lambda block body
 */
fun irLambda(
    context: KneeContext,
    parent: IrDeclarationParent,
    valueParameters: List<IrType>,
    returnType: IrType,
    suspend: Boolean = false,
    content: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
): IrFunctionExpression = irLambda(
    context = context,
    parent = parent,
    suspend = suspend,
    content = {
        it.returnType = returnType
        valueParameters.forEachIndexed { i, type -> it.addValueParameter("arg$i", type) }
        content(it)
    }
)

fun irLambda(
    context: KneeContext,
    parent: IrDeclarationParent,
    suspend: Boolean = false,
    content: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
): IrFunctionExpression {
    val lambda = context.factory.buildFun {
        startOffset = SYNTHETIC_OFFSET
        endOffset = SYNTHETIC_OFFSET
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        name = Name.special("<anonymous>")
        visibility = DescriptorVisibilities.LOCAL
        isSuspend = suspend
    }.apply {
        this.parent = parent
        body = DeclarationIrBuilder(context.plugin, this.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).irBlockBody {
            content(this@apply)
        }
    }
    return IrFunctionExpressionImpl(
        startOffset = SYNTHETIC_OFFSET,
        endOffset = SYNTHETIC_OFFSET,
        type = run {
            when (suspend) {
                false -> context.symbols.klass(KotlinIds.FunctionX(lambda.valueParameters.size))
                true -> context.symbols.klass(KotlinIds.SuspendFunctionX(lambda.valueParameters.size))
                // true -> context.irBuiltIns.suspendFunctionN(lambda.valueParameters.size)
            }.typeWith(*lambda.valueParameters.map { it.type }.toTypedArray(), lambda.returnType)
        },
        origin = IrStatementOrigin.LAMBDA,
        function = lambda
    )
}

fun IrBuilderWithScope.irError(symbols: KneeSymbols, message: String): IrExpression {
    val error = symbols.functions(KotlinIds.error).single()
    return irCall(error).apply {
        putValueArgument(0, irString(message))
    }
}


// Not IrType.hashCode because it seems that it's not stable in some scenarios
// No, this doesn't seem stable either, resorting to different strategies
/* fun IrType.disambiguationHash(): Int {
    val data = mutableListOf<String>().also { disambiguationEntries(it) }
    return data.hashCode() // .also { println("disambiguation of $this: $it <- ${data.joinToString(separator = "")}") }
}

private fun IrType.disambiguationEntries(list: MutableList<String>) {
    if (this !is IrSimpleType) {
        error("Can't compute disambiguationHash because $this is not a IrSimpleType ($list).")
    }
    list.add(classOrNull?.owner?.classId?.asSingleFqName()?.asString() ?: "null")
    list.add(arguments.size.toString())
    if (arguments.isNotEmpty()) {
        list.add("{")
        arguments.forEach {
            when (it) {
                is IrTypeProjection -> {
                    list.add(it.variance.name)
                    it.type.disambiguationEntries(list)
                }
                is IrStarProjection -> list.add("*")
                // else -> list.add(it)
                else -> error("Can't compute disambiguationHash because $it is not a IrTypeProjection/IrStarProjection ($list).")
            }
            list.add(",")
        }
        list.add("}")
    }
} */


/**
 * When migrating a declaration to top level, name must be made unique
 * within that package, so we can't simply use the declaration name as is.
 */
// val IrDeclarationWithName.uniqueName: Name get() = uniqueName { it }
/* inline fun IrDeclarationWithName.uniqueName(special: Boolean = name.isSpecial, map: (String) -> String): Name {
    val prefix = when (val p = parent) {
        is IrDeclarationWithName -> p.fqNameWithoutPackageName.pathSegments().joinToString("_") {
            require(!it.isSpecial) { "Ancestor of $name, $it has special characters. Not sure how to handle this." }
            it.asString()
        }
        is IrPackageFragment -> null
        else -> error("Parent of $name is invalid: $parent")
    }
    return name.map(special) {
        listOfNotNull(prefix, map(it)).joinToString("_")
    }
} */


/**
 * Creates a local function, like in:
 * fun main() {
 *     fun doStuff(int: Int) = int + 1
 *     println(doStuff(41))
 * }
 *
 * The function should have:
 * - A name
 * - Origin: [IrDeclarationOrigin.LOCAL_FUNCTION]
 * - Visibility: [DescriptorVisibilities.LOCAL]
 * Sample internal representation:
 *
 * FUN LOCAL_FUNCTION name:mapIntToInt visibility:local modality:FINAL <> (int:kotlin.Int) returnType:kotlin.Int
 *   VALUE_PARAMETER name:int index:0 type:kotlin.Int
 *   BLOCK_BODY
 *     RETURN type=kotlin.Nothing from='local final fun mapIntToInt (int: kotlin.Int): kotlin.Int declared in io.deepmedia.tools.knee.sample.xxx'
 *       CALL 'public final fun plus (other: kotlin.Int): kotlin.Int [external,operator] declared in kotlin.Int' type=kotlin.Int origin=PLUS
 *         $this: GET_VAR 'int: kotlin.Int declared in io.deepmedia.tools.knee.sample.xxx.mapIntToInt' type=kotlin.Int origin=null
 *         other: CONST Int type=kotlin.Int value=1
 *
 * Note that the function can be passed to other functions accepting a lambda by using [irFunctionReference] on it.
 * In this case at usage site we will see:
 *   lambda: FUNCTION_REFERENCE 'local final fun mapIntToInt (int: kotlin.Int): kotlin.Int declared in io.deepmedia.tools.knee.sample.xxx' type=kotlin.reflect.KFunction1<kotlin.Int, kotlin.Int> origin=null reflectionTarget=<same>
 */
/*fun IrFactory.buildLocalFun(
    parent: IrFunction,
    name: Name,
    suspend: Boolean = false,
    returnType: IrType
): IrSimpleFunction {
    return buildFun {
        startOffset = SYNTHETIC_OFFSET
        endOffset = SYNTHETIC_OFFSET
        origin = IrDeclarationOrigin.LOCAL_FUNCTION
        this.name = name
        visibility = DescriptorVisibilities.LOCAL
        isSuspend = suspend
        this.returnType = returnType
    }.apply {
        this.parent = parent
    }
}*/

/* @Suppress("RecursivePropertyAccessor")
val IrDeclarationWithName.fqNameWithoutPackageName: FqName
    get() = when (val parent = parent) {
        is IrDeclarationWithName -> parent.fqNameWithoutPackageName.child(name)
        is IrPackageFragment -> FqName(name.asString())
        else -> error("Parent of $name is invalid: $parent")
    }


fun IrBuilderWithScope.irFunctionReference(type: IrType, function: IrFunction) = irFunctionReference(
    type = type,
    symbol = function.symbol,
    typeArgumentsCount = function.typeParameters.size,
    valueArgumentsCount = function.valueParameters.size
)
*/

