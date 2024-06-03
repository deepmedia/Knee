package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenFunction
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeUpwardFunction
import io.deepmedia.tools.knee.plugin.compiler.features.KneeInterface
import io.deepmedia.tools.knee.plugin.compiler.functions.*
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.context.KneeLogger
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.PlatformIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.kneeInvokeKnSuspend
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.useEnv
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*

fun processUpwardFunction(
    function: KneeUpwardFunction,
    context: KneeContext,
    codegen: KneeCodegen
) {
    val signature = UpwardFunctionSignature(function.source, function.kind, context.symbols, context.mapper)
    val implementation = function.makeIr(context, signature)
    function.makeCodegen(codegen, signature, implementation, context.log)
}

/**
 * We put the function inside the companion object of the class where the reverse function was implemented.
 * Such class is returned by [KneeInterface.irImplementation] - for interfaces, it's "Knee${interface}".
 *
 * Note that we use the companion object of it, we want the function to be @JvmStatic.
 * Another option was using the companion object of the interface itself, but there's a rule in Kotlin where
 * static members of the companion object of an interface must be public, which is unacceptable for us.
 *
 * So we just use [KneeInterface.irImplementation] since it already exists.
 * It's tricky because the codegen version of [KneeInterface.irImplementation] is actually used by
 * regular functions (not reverse), but since we use the companion object there's no overlap.
 */
private fun KneeUpwardFunction.makeCodegen(
    codegen: KneeCodegen,
    signature: UpwardFunctionSignature,
    implementation: IrSimpleFunction,
    logger: KneeLogger
) {
    val spec = FunSpec
        .builder(signature.jniInfo.name(includeAncestors = false).asString())
        .addModifiers(KModifier.PRIVATE)
        .addAnnotation(ClassName.bestGuess("kotlin.jvm.JvmStatic"))
        .returns((if (signature.isSuspend) signature.suspendResult else signature.result).encodedType.jvmOrNull?.name ?: UNIT)

    // Parameters
    signature.extraParameters.forEach { (param, codec) ->
        val name = param.asStringSafeForCodegen(true)
        spec.addParameter(name, codec.encodedType.jvmOrNull!!.name)
    }
    signature.regularParameters.forEach { (param, codec) ->
        val name = param.asStringSafeForCodegen(true)
        spec.addParameter(name, codec.encodedType.jvmOrNull!!.name)
    }

    // Code block
    with(UpwardFunctionsCodegen) {
        // The receiver should be received as itself (no long tricks) and needs no mapping
        val codecContext = CodegenCodecContext(source.symbol, true, logger)
        if (!signature.isSuspend) {
            spec.addCode(CodeBlock.builder().apply {
                codegenInvoke(signature, "val res = ", codecContext)
                codegenReceive("res", signature, "return ", codecContext)
            }.build())
        } else {
            // Function has two prefixes - receiver and then suspendInvoker passed as a long.
            // The function should return a SuspendInvocation object. Helper signature:
            // fun <T> kneeInvokeKnSuspend(invoker: Long, block: suspend () -> T): KneeSuspendInvocation<T>
            spec.addCode(CodeBlock.builder().apply {
                val invoke = MemberName("io.deepmedia.tools.knee.runtime.compiler", "kneeInvokeKnSuspend")
                beginControlFlow("return %M<%T>(${UpwardFunctionSignature.Extra.SuspendInvoker}) {", invoke, signature.result.encodedType.jvmOrNull?.name ?: UNIT)
                codegenInvoke(signature, "val res = ", codecContext)
                codegenReceive("res", signature, "", codecContext)
                endControlFlow()
            }.build())
        }
    }

    // Save
    if (codegen.verbose) spec.addKdoc("knee:reverse-functions")
    val product = CodegenFunction(spec)
    codegen.prepareContainer(
        declaration = implementation,
        importInfo = kind.importInfo,
        detectPropertyAccessors = false, // we don't generate properties at all in the companion object
        createCompanionObject = true
    ).addChild(product)
    codegenProducts.add(product)
}

private fun KneeUpwardFunction.makeIr(context: KneeContext, signature: UpwardFunctionSignature): IrSimpleFunction {
    val envType = context.symbols.klass(CInteropIds.CPointer)
        .typeWith(context.symbols.typeAliasUnwrapped(PlatformIds.JNIEnvVar))

    val kind = kind as KneeUpwardFunction.Kind.InterfaceMember

    // reuse function if it exists already. this happens in the case of reverse properties
    // where we prefer to add getter / setter there to properly configure them
    val implementation = implementation ?: kind.parent.irImplementation.addFunction {
        name = source.name
        isSuspend = source.isSuspend
        modality = Modality.FINAL
        origin = source.origin
        // Without this, suspend function generation fails!
        startOffset = SYNTHETIC_OFFSET
        endOffset = SYNTHETIC_OFFSET
    }.also { this.implementation = it }

    return implementation.apply {
        // Configure return type. Not source.returnType, that will fail for generics
        returnType = signature.result.localIrType

        // Configure value parameters. First option is 'copyParameterDeclarationsFrom(source)'
        // but that copies type parameters too, fails for generics. We have concrete types.
        // Use the import susbstitution map instead, or TODO: use signature value parameters
        copyValueParametersFrom(source, kind.importInfo?.substitutionMap ?: emptyMap())

        // This function overrides the source function
        // Could also += source.overriddenSymbols, not sure if needed, we're not doing it elsewhere
        overriddenSymbols += source.symbol

        val logPrefix = "ReverseFunctions.kt(${source.fqNameWhenAvailable})"
        body = DeclarationIrBuilder(context.plugin, symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).irBlockBody {
            context.log.injectLog(this, "$logPrefix INVOKED, retrieving jvm info")
            val jvmMethodOwner = irTemporary(kind.parent.irGetMethodOwner(this))
            val jvmMethod = irTemporary(kind.parent.irGetMethod(this, signature))
            val jvmObject = irTemporary(kind.parent.irGetJvmObject(this))
            val args = valueParameters
            if (!signature.isSuspend) {
                +irReturn(irCall(
                    callee = context.symbols.functions(useEnv).single()
                ).apply {
                    extensionReceiver = kind.parent.irGetVirtualMachine(this@irBlockBody)
                    putTypeArgument(0, signature.result.localIrType)
                    putValueArgument(0, irLambda(
                        context = context,
                        parent = parent,
                        content = { lambda ->
                            lambda.returnType = signature.result.localIrType
                            val env = lambda.addValueParameter("_env", envType)

                            context.log.injectLog(this, "$logPrefix got environment, preparing the JVM call")
                            val codecContext = IrCodecContext(source.symbol, env, true, context.log)
                            with(UpwardFunctionsIr) {
                                val raw = irInvoke(context.symbols, args, signature, codecContext, jvmObject, jvmMethodOwner, jvmMethod, signature.result.encodedType)
                                +irReturn(irReceive(raw, signature, codecContext))
                            }
                        }
                    ))
                })
            } else {
                // See kneeInvokeKnSuspend signature in runtime
                context.log.injectLog(this, "$logPrefix suspend machinery started")
                +irReturn(irCall(context.symbols.functions(kneeInvokeKnSuspend).single()).apply {
                    putTypeArgument(0, signature.result.encodedType.knOrNull ?: context.symbols.builtIns.unitType)
                    putTypeArgument(1, signature.result.localIrType)
                    putValueArgument(0, kind.parent.irGetVirtualMachine(this@irBlockBody))
                    putValueArgument(1, irLambda(context, parent) { lambda ->
                        val env = lambda.addValueParameter("_env", envType)
                        val invoker = lambda.addValueParameter("_invoker", context.symbols.builtIns.longType)
                        lambda.returnType = signature.suspendResult.localIrType
                        with(UpwardFunctionsIr) {
                            val codecContext = IrCodecContext(source.symbol, env, true, context.log)
                            context.log.injectLog(this@irBlockBody, "$logPrefix preparing the JVM call")
                            val raw = irInvoke(context.symbols, args, signature, codecContext, jvmObject, jvmMethodOwner, jvmMethod, signature.suspendResult.encodedType, invoker)
                            context.log.injectLog(this@irBlockBody, "$logPrefix received the invocation token")
                            +irReturn(irReceive(raw, signature, codecContext, suspendToken = true))
                        }
                    })
                    putValueArgument(2, irLambda(context, parent) { lambda ->
                        val env = lambda.addValueParameter("_env", envType)
                        val raw = lambda.addValueParameter("_result", signature.result.encodedType.knOrNull ?: context.symbols.builtIns.unitType)
                        lambda.returnType = signature.result.localIrType
                        with(UpwardFunctionsIr) {
                            val codecContext = IrCodecContext(source.symbol, env, true, context.log)
                            context.log.injectLog(this@irBlockBody, "$logPrefix received the suspend function result. unwrapping it")
                            +irReturn(irReceive(irGet(raw), signature, codecContext))
                        }
                    })
                })
            }
        }
    }.also {
        irProducts.add(it)
    }
}