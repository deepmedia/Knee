package io.deepmedia.tools.knee.plugin.compiler.functions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.utils.asStringSafeForCodegen
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.isGetter
import org.jetbrains.kotlin.ir.util.isSetter

/**
 * Codegen companion of [UpwardFunctionsIr].
 */
object UpwardFunctionsCodegen {

    /**
     * Calls the local function from the jni wrapper function, mapping all inputs.
     * Returns the raw output, unprocessed. Use [codegenReceive] to process it.
     */
    fun CodeBlock.Builder.codegenInvoke(
        signature: UpwardFunctionSignature,
        prefix: String, // e.g. "return "
        codecContext: CodegenCodecContext,
    ) {
        // addStatement("println(\"DEBUG: ${codecContext.functionSymbol.owner.name} (reverse) invoked\")")
        // Create the code block that invokes the jni function
        val parameters = LinkedHashMap<String, String>() // order matters

        // PARAMETERS
        // Regular parameters, to be propagated after proper mapping
        signature.regularParameters.forEach { (param, codec) ->
            val name = param.asStringSafeForCodegen(true)
            with(codec) { parameters[name] = codegenDecode(codecContext, name) }
        }

        // CALL
        val receiver = UpwardFunctionSignature.Extra.Receiver
        val func = codecContext.functionSymbol!!.owner as IrSimpleFunction
        when {
            func.isSetter -> {
                check(parameters.size == 1) { "Setter should have only 1 parameter, found: $parameters" }
                addStatement("$receiver.${func.correspondingPropertySymbol!!.owner.name} = ${parameters.values.single()}")
                addStatement("${prefix}%T", UNIT)
            }
            func.isGetter -> {
                check(parameters.size == 0) { "Getter should have no parameters, found: $parameters" }
                addStatement("$prefix$receiver.${func.correspondingPropertySymbol!!.owner.name}")
            }
            else -> {
                val parametersFormat = parameters.keys.joinToString { "%${it}:L" }
                addNamed("$prefix$receiver.${func.name}($parametersFormat)\n", parameters)
            }
        }
    }

    fun CodeBlock.Builder.codegenReceive(
        rawValue: String,
        signature: UpwardFunctionSignature,
        prefix: String, // e.g. "return "
        codecContext: CodegenCodecContext,
        suspendToken: Boolean = false,
    ) {
        val returnType = if (suspendToken) signature.suspendResult else signature.result
        val decodedValue = when {
            !returnType.needsCodegenConversion -> rawValue
            else -> with(returnType) { codegenEncode(codecContext, rawValue) }
        }
        addStatement("$prefix$decodedValue")
    }


}