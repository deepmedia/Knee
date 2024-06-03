package io.deepmedia.tools.knee.plugin.compiler.functions

import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.callStaticMethod
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * IR companion of [UpwardFunctionsCodegen].
 */
object UpwardFunctionsIr {

    /**
     * Calls the JVM function from IR using Jni utilities, mapping all inputs.
     * Returns the raw output, not mapped.
     */
    fun IrStatementsBuilder<*>.irInvoke(
        symbols: KneeSymbols,
        inputs: List<IrValueParameter>,
        signature: UpwardFunctionSignature,
        codecContext: IrCodecContext,
        jreceiver: IrVariable,
        jmethodOwner: IrVariable,
        jmethod: IrVariable,
        returnJniType: JniType,
        suspendInvoker: IrValueParameter? = null
    ): IrExpression {
        val logPrefix = "ReverseFunctionsIr.irInvoke(${codecContext.functionSymbol!!.owner.fqNameWhenAvailable})"

        // Take care of prefixes
        codecContext.logger.injectLog(this, "$logPrefix START")
        val prefixInputs = signature.extraParameters.map { (param, codec) ->
            codecContext.logger.injectLog(this, "$logPrefix ENCODING prefix $param with $codec")
            with(codec) {
                irEncode(codecContext, local = when (param) {
                    UpwardFunctionSignature.Extra.Receiver -> jreceiver
                    UpwardFunctionSignature.Extra.SuspendInvoker -> suspendInvoker!!
                    else -> error("Unexpected prefix parameter: $param")
                })
            }
        }

        // Encode all inputs
        val mappedInputs = signature.regularParameters.map { (param, codec) ->
            codecContext.logger.injectLog(this, "$logPrefix ENCODING param $param with $codec")
            with(codec) { irEncode(codecContext, inputs.first { it.name == param }) }
        }

        val function = callStaticMethod(returnJniType.nameOfCallMethodFunction)
        codecContext.logger.injectLog(this, "$logPrefix INVOKING $function")

        // By design we pass the receiver as argument instead of JNI receiver, because otherwise
        // we'd have to add the JNI wrapper function inside the interface. We use companion object + JvmStatic instead.
        return irCall(
            symbols.functions(function).single()
        ).apply {
            extensionReceiver = irGet(codecContext.environment)
            putValueArgument(0, irGet(jmethodOwner))
            putValueArgument(1, irGet(jmethod))
            putValueArgument(2, irVararg(
                elementType = symbols.builtIns.anyType.makeNullable(),
                values = prefixInputs + mappedInputs
            ))
        }
    }

    private val JniType.nameOfCallMethodFunction: String get() {
        return when (this) {
            is JniType.Void -> "Void"
            is JniType.Object, is JniType.Array -> "Object"
            is JniType.Int -> "Int"
            is JniType.BooleanAsUByte -> "Boolean"
            is JniType.Float -> "Float"
            is JniType.Double -> "Double"
            is JniType.Byte -> "Byte"
            is JniType.Long -> "Long"
        }
    }


    /**
     * Process the result of [irInvoke] - before returning it might need conversion.
     */
    fun IrStatementsBuilder<*>.irReceive(
        rawValue: IrExpression,
        signature: UpwardFunctionSignature,
        codecContext: IrCodecContext,
        suspendToken: Boolean = false
    ): IrExpression {
        val logPrefix = "ReverseFunctionsIr.irReceive(${codecContext.functionSymbol!!.owner.fqNameWhenAvailable})"

        val returnType = if (suspendToken) signature.suspendResult else signature.result
        if (!returnType.needsIrConversion) return rawValue
        return with(returnType) {
            codecContext.logger.injectLog(this@irReceive, "$logPrefix DECODING return type with $returnType")
            irDecode(codecContext, irTemporary(rawValue, "result"))
        }
    }
}