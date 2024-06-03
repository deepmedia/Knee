package io.deepmedia.tools.knee.plugin.compiler.functions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.utils.asStringSafeForCodegen
import org.jetbrains.kotlin.name.Name

/**
 * Codegen companion of [DownwardFunctionsIr].
 */
object DownwardFunctionsCodegen {

    /**
     * Calls jni from the local function. The difference with the IR counterpart of this function
     * is that the jni/bridge function does not exist and we must generate it here (and return it).
     */
    fun CodeBlock.Builder.codegenInvoke(
        signature: DownwardFunctionSignature,
        bridgeFunctionName: Name,
        prefix: String, // e.g. "return "
        codecContext: CodegenCodecContext,
    ): FunSpec.Builder {

        // Create the bridge, jni external function
        // println("codegenLocalToJni: $bridgeFunctionName")
        val bridgeName = bridgeFunctionName.asString()
        val bridgeSpec = FunSpec
            .builder(bridgeName)
            .addModifiers(KModifier.EXTERNAL, KModifier.PRIVATE)
            .returns((if (signature.isSuspend) signature.suspendResult else signature.result).encodedType.jvmOrNull?.name ?: UNIT)

        // Create the code block that invokes the jni function
        val callParameters = mutableMapOf<String, String>()

        // PARAMETERS
        // Class members need to pass "this.knee" to the external function
        /* signature.extraParameters.forEach { (param, type) ->
            val name = param.asStringSafeForCodegen()
            bridgeSpec.addParameter(name, type.kpType)
            if (param == FunctionSignature.Extra.Receiver) {
                callParameters[name] = ClassHandleName
            } else {
                callParameters[name] = name
            }
        } */
        signature.extraParameters.forEach { (param, codec) ->
            val name = param.asStringSafeForCodegen(true)
            with(codec) {
                bridgeSpec.addParameter(name, encodedType.jvmOrNull!!.name)
                callParameters[name] = codegenEncode(
                    codegenContext = codecContext,
                    local = if (param == DownwardFunctionSignature.Extra.ReceiverInstance) "this" else name
                )
            }
        }
        // Regular parameters, to be propagated after proper mapping
        signature.regularParameters.forEach { (param, codec) ->
            val name = param.asStringSafeForCodegen(true)
            // println("codegenLocalToJni param: ${param.name} safe: $name")
            with(codec) {
                bridgeSpec.addParameter(name, encodedType.jvmOrNull!!.name)
                callParameters[name] = codegenEncode(codecContext, name)
            }
        }

        // CALL
        // Need to flatten the call parameters in a single invocation line
        addNamed(
            format = "$prefix`$bridgeName`(${bridgeSpec.parameters.joinToString { "%${it.name}:L" }})\n",
            arguments = callParameters
        )
        return bridgeSpec
    }

    /**
     * Process the result of [codegenInvoke] - before returning it to local,
     * it might need conversion.
     */
    fun CodeBlock.Builder.codegenReceive(
        rawValue: String,
        signature: DownwardFunctionSignature,
        prefix: String, // e.g. "return "
        codecContext: CodegenCodecContext,
        suspendToken: Boolean = false
    ) {
        val returnType = if (suspendToken) signature.suspendResult else signature.result
        val decodedValue = when {
            !returnType.needsCodegenConversion -> rawValue
            else -> with(returnType) { codegenDecode(codecContext, rawValue) }
        }
        addStatement("$prefix$decodedValue")
    }
}