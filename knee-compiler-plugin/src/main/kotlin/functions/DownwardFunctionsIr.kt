package io.deepmedia.tools.knee.plugin.compiler.functions

import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.functions.DownwardFunctionsIr.irInvoke
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/**
 * IR companion of [DownwardFunctionsCodegen].
 */
object DownwardFunctionsIr {

    /**
     * Calls the original, local function from bridge, mapping all inputs.
     * Returns the raw output, not mapped.
     */
    fun IrStatementsBuilder<*>.irInvoke(
        inputs: List<IrValueParameter>,
        local: IrFunction,
        signature: DownwardFunctionSignature,
        codecContext: IrCodecContext,
    ): IrExpression {
        val logPrefix = "FunctionsIr.irInvoke(${local.fqNameWhenAvailable})"
        codecContext.logger.injectLog(this, "$logPrefix START")

        return irCall(local).apply {
            val hasReceiver = signature.extraParameters.firstOrNull { it.first == DownwardFunctionSignature.Extra.ReceiverInstance }
            hasReceiver?.let { (name, codec) ->
                val param = inputs.first { it.name == name }
                codecContext.logger.injectLog(this@irInvoke, "$logPrefix Decoding dispatch receiver $name with $codec")
                dispatchReceiver = with(codec) { irDecode(codecContext, param) }
            }
            signature.regularParameters.forEachIndexed { index, (param, codec) ->
                with(codec) {
                    // note: targetIndex != index because of copy parameters!
                    val inputIndex = index + signature.knPrefixParameters.size + signature.extraParameters.size
                    val targetIndex = local.valueParameters.indexOfFirst { it.name == param }
                    codecContext.logger.injectLog(this@irInvoke, "$logPrefix Decoding parameter $param with $codec")
                    putValueArgument(targetIndex, irDecode(codecContext, inputs[inputIndex]))
                }
            }
            /* signature.knCopyParameters.forEach { (param, indexToBeCopied) ->
                val targetIndex = local.valueParameters.indexOfFirst { it.name == param }
                putValueArgument(targetIndex, irGet(inputs[indexToBeCopied]))
            } */
        }
    }

    /**
     * Process the result of [irInvoke] - before returning it to bridge,
     * it might need conversion.
     */
    fun IrStatementsBuilder<*>.irReceive(
        rawValue: IrExpression,
        signature: DownwardFunctionSignature,
        codecContext: IrCodecContext,
        suspendToken: Boolean = false
    ): IrExpression {
        val returnType = if (suspendToken) signature.suspendResult else signature.result
        if (!returnType.needsIrConversion) return rawValue
        return with(returnType) {
            irEncode(codecContext, irTemporary(rawValue, "result"))
        }
    }
}