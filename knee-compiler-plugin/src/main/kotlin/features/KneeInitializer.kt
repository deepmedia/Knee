package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

/* class KneeInit(source: IrSimpleFunction) : KneeFeature<IrSimpleFunction>(source, "KneeInit") {

    init {
        source.requireNotComplex(this)
        require(source.isTopLevel) { "$this must be a top level function." }
    }
}

fun KneeInit.validate(context: KneeContext) {
    val returnType = source.returnType
    val expectedReturnType = context.symbols.builtIns.unitType
    require(returnType == expectedReturnType) {
        "$this must return ${expectedReturnType.dumpKotlinLike()} (not ${returnType.dumpKotlinLike()})"
    }
    val arg0 = source.valueParameters.firstOrNull()?.type
    val expectedArg0 = context.symbols.jniEnvironmentType
    require(arg0 == expectedArg0) {
        "$this first parameter must be ${(expectedArg0 as IrType).dumpKotlinLike()} (not ${arg0?.dumpKotlinLike()})"
    }
}


private fun DeclarationIrBuilder.irInitLambdas(context: KneeContext, inits: List<KneeInit>): IrExpression {
    val symbols = context.symbols
    val type = symbols.klass(functionXInterface(1)).typeWith(symbols.jniEnvironmentType, symbols.builtIns.unitType)
    return irListOf(symbols, type, inits.map { init ->
        irLambda(
            context = context,
            parent = this@irInitLambdas.parent,
            valueParameters = listOf(symbols.jniEnvironmentType),
            returnType = symbols.builtIns.unitType,
            content = { lambda ->
                val environment = irGet(lambda.valueParameters[0])
                +irCall(init.source).apply { putValueArgument(0, environment) }
            }
        )
    })
}*/


class KneeInitializer(val expression: IrCall)