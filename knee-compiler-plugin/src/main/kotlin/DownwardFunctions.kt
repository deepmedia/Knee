package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenFunction
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeDownwardFunction
import io.deepmedia.tools.knee.plugin.compiler.features.KneeDownwardFunction.Kind
import io.deepmedia.tools.knee.plugin.compiler.functions.DownwardFunctionSignature
import io.deepmedia.tools.knee.plugin.compiler.functions.DownwardFunctionsCodegen
import io.deepmedia.tools.knee.plugin.compiler.functions.DownwardFunctionsIr
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.context.KneeLogger
import io.deepmedia.tools.knee.plugin.compiler.context.KneeOrigin
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.kneeInvokeJvmSuspend
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.rethrowNativeException
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name

fun processDownwardFunction(function: KneeDownwardFunction, context: KneeContext, codegen: KneeCodegen, initInfo: InitInfo) {
    val signature = DownwardFunctionSignature(function.source, function.kind, context)
    function.makeIr(context, signature, initInfo)
    function.makeCodegen(codegen, signature, context.log)
}

private fun KneeDownwardFunction.makeCodegen(codegen: KneeCodegen, signature: DownwardFunctionSignature, logger: KneeLogger) {
    // Unlike IR, we have to generate both the bridge function and the local function.
    // First we make the local function, whose implementation will call the bridge
    val localName = source.name.asString()
    val bridgeName = signature.jniInfo.name(includeAncestors = false)

    val bridgeSpec: FunSpec.Builder
    val localSpec: FunSpec.Builder = when {
        source.isSetter -> FunSpec.setterBuilder()
        source.isGetter -> FunSpec.getterBuilder()
        kind is Kind.ClassConstructor -> FunSpec
            .constructorBuilder()
            .addModifiers(source.visibility.asModifier())
        else -> FunSpec
            .builder(localName)
            .addModifiers(source.visibility.asModifier())
            .apply {
                if ((source as? IrSimpleFunction)?.isOperator == true) {
                    addModifiers(KModifier.OPERATOR)
                }
                if (source.isSuspend) {
                    addModifiers(KModifier.SUSPEND)
                }
                if (kind is Kind.InterfaceMember) {
                    addModifiers(KModifier.OVERRIDE)
                }
            }
    }
    localSpec.apply {
        // RETURN TYPE
        // Add it unless getter or setter or constructor because KotlinPoet will throw in this case
        // E.g. 'IllegalStateException: get() cannot have a return type'
        signature.result.let {
            if (!source.isGetter && !source.isSetter && kind !is Kind.ClassConstructor) {
                returns(it.localCodegenType.name)
            }
        }
        // PARAMETERS
        // Exclude prefixes, they only refer to bridge functions
        signature.regularParameters.forEach { (param, codec) ->
            val name = param.asStringSafeForCodegen(true)
            val defaultValue = source.valueParameters.firstOrNull { it.name == param }?.defaultValueForCodegen(expectSources)
            // addParameter(name, codec.localCodegenType.name)
            addParameter(ParameterSpec.builder(name, codec.localCodegenType.name)
                .defaultValue(defaultValue)
                .build())
        }
        // BODY
        with(DownwardFunctionsCodegen) {
            val codecContext = CodegenCodecContext(source.symbol, false, logger)
            if (signature.isSuspend) {
                // public suspend fun <T> kneeInvokeJvmSuspend(block: (KneeSuspendInvoker<T>) -> Long): T
                addCode(CodeBlock.builder().apply {
                    val invoke = MemberName("io.deepmedia.tools.knee.runtime.compiler", "kneeInvokeJvmSuspend")
                    beginControlFlow("val res = %M { ${DownwardFunctionSignature.Extra.SuspendInvoker} ->", invoke)
                    bridgeSpec = codegenInvoke(signature, bridgeName, "val token = ", codecContext)
                    codegenReceive("token", signature, "", codecContext, suspendToken = true)
                    endControlFlow()
                    // Map the raw jni result from kneeInvokeJvmSuspend into the local world
                    codegenReceive("res", signature, "return ", codecContext)
                }.build())
            } else if (kind is Kind.ClassConstructor) {
                callThisConstructor(CodeBlock.builder().apply {
                    beginControlFlow("Unit.run")
                    bridgeSpec = codegenInvoke(signature, bridgeName, "val res = ", codecContext)
                    bridgeSpec.addAnnotation(ClassName.bestGuess("kotlin.jvm.JvmStatic"))
                    codegenReceive("res", signature, "", codecContext)
                    endControlFlow()
                }.build())
            } else {
                addCode(CodeBlock.builder().apply {
                    bridgeSpec = codegenInvoke(signature, bridgeName, "val res = ", codecContext)
                    codegenReceive("res", signature, "return ", codecContext)
                }.build())
            }
        }
    }

    // Save products
    if (codegen.verbose) localSpec.addKdoc("knee:functions")
    if (codegen.verbose) bridgeSpec.addKdoc("knee:functions:bridge")
    val localFun = CodegenFunction(localSpec)
    val bridgeFun = CodegenFunction(bridgeSpec)

    // TODO: use FunctionSignature.JniInfo.owner for at least one of this of these containers
    val localContainer = kind.property?.codegenImplementation ?: when (kind) {
        is Kind.InterfaceMember -> kind.owner.codegenImplementation
        else -> codegen.prepareContainer(source, kind.importInfo)
    }
    val bridgeContainer = when (kind) { // skip properties in this case
        is Kind.InterfaceMember -> kind.owner.codegenImplementation
        is Kind.ClassConstructor -> codegen.prepareContainer(source, kind.importInfo, createCompanionObject = true)
        else -> codegen.prepareContainer(source, kind.importInfo, detectPropertyAccessors = false)
    }

    localContainer.addChild(localFun)
    bridgeContainer.addChild(bridgeFun)
    codegenProducts.add(localFun)
    codegenProducts.add(bridgeFun)

    if (kind is Kind.InterfaceMember && kind.property == null) {
        // IMPORTANT: use unsubstituted params here! In case of generics, the base interface must have the raw
        // parameters with raw unknown types. We expose this info in the signature with the unsubstituted prefix.
        // Also use addChildIfNeeded for the same reason we do so in knee:properties:abstract-interface-child
        // (User might be importing Flow<Int> and Flow<String>, but only one function goes to Flow<T>)
        val function = FunSpec.builder(source.name.asString()).apply {
            if (codegen.verbose) addKdoc("knee:functions:abstract-interface-child")
            addModifiers(KModifier.ABSTRACT)
            if (signature.isSuspend) addModifiers(KModifier.SUSPEND)
            returns(signature.unsubstitutedReturnTypeForCodegen)
            signature.unsubstitutedValueParametersForCodegen.forEach { (name, type) ->
                addParameter(name.asString(), type)
            }
        }
        kind.owner.codegenClone?.addChildIfNeeded(CodegenFunction(function))
    }
}

private fun KneeDownwardFunction.makeIr(context: KneeContext, signature: DownwardFunctionSignature, initInfo: InitInfo) {
    val file = kind.importInfo?.file ?: source.file
    val property = file.addSimpleProperty(
        plugin = context.plugin,
        type = context.symbols.typeAliasUnwrapped(CInteropIds.COpaquePointer),
        name = signature.jniInfo.name(includeAncestors = true)
    ) {
        val staticCFunctionCall = irCall(
            // staticCFunction<P0, P1, P2, ..., ReturnType>(...)
            callee = context.symbols.functions(CInteropIds.staticCFunction).single {
                it.owner.typeParameters.size == 1 +
                        signature.knPrefixParameters.size +
                        signature.extraParameters.size +
                        signature.regularParameters.size
            }
        )
        // only argument of staticCFunction is a lambda
        staticCFunctionCall.putValueArgument(0, irLambda(
            context = context,
            parent = parent,
            content = { lambda ->
                // configure lambda and staticCFunction types
                var args = 0
                signature.knPrefixParameters.forEach { (name, type) ->
                    lambda.addValueParameter(name, type, KneeOrigin.KNEE)
                    staticCFunctionCall.putTypeArgument(args++, type)
                }
                signature.extraParameters.forEach { (param, codec) ->
                    val type = codec.encodedType.knOrNull!!
                    lambda.addValueParameter(param, type)
                    staticCFunctionCall.putTypeArgument(args++, type)
                }
                signature.regularParameters.forEach { (param, codec) ->
                    val type = codec.encodedType.knOrNull!!
                    val sourceParam = source.valueParameters.first { it.name == param }
                    // defaultValue = null is very important here because we are changing the type, potentially
                    lambda.valueParameters += sourceParam.copyTo(lambda, index = args, type = type, name = param, defaultValue = null)
                    staticCFunctionCall.putTypeArgument(args++, type)
                }
                run {
                    val resultOrSuspendResult = (if (signature.isSuspend) signature.suspendResult else signature.result)
                        .encodedType.knOrNull ?: context.symbols.builtIns.unitType
                    lambda.returnType = resultOrSuspendResult
                    staticCFunctionCall.putTypeArgument(args, resultOrSuspendResult)
                }


                // actual body where we call the user-defined function and do mapping
                val environment =
                    lambda.valueParameters.first { it.name == DownwardFunctionSignature.KnPrefix.JniEnvironment }
                val codecContext = IrCodecContext(
                    functionSymbol = source.symbol,
                    environment = environment,
                    reverse = false,
                    logger = context.log
                )
                val logPrefix = "Functions.kt(${source.fqNameWhenAvailable})"
                context.log.injectLog(this, "$logPrefix CALLED FROM JVM")
                +irReturn(if (!signature.isSuspend) {
                    with(DownwardFunctionsIr) {
                        // val raw = irInvoke(lambda.valueParameters, source, signature, codecContext)
                        // irReceive(raw, signature, codecContext)
                        val catch = buildVariable(parent, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, IrDeclarationOrigin.CATCH_PARAMETER, Name.identifier("t"), context.symbols.builtIns.throwableType)
                        irTry(
                            type = signature.result.encodedType.knOrNull ?: context.symbols.builtIns.unitType,
                            tryResult = irComposite {
                                val raw = irInvoke(lambda.valueParameters, source, signature, codecContext)
                                +irReceive(raw, signature, codecContext)
                            },
                            catches = listOf(irCatch(
                                catchParameter = catch,
                                result = irComposite {
                                    // Forward the error to the JVM and swallow it on the native side.
                                    +irCall(context.symbols.functions(rethrowNativeException).single()).apply {
                                        extensionReceiver = irGet(environment)
                                        putValueArgument(0, irGet(catch))
                                    }
                                    // Return 'something' here otherwise compilation fails (I think).
                                    // It will never be used anyway because the JVM will throw due to previous command.
                                    +when (val type = signature.result.encodedType) {
                                        is JniType.Void -> irUnit()
                                        is JniType.Object -> irNull()
                                        is JniType.Int -> irInt(0)
                                        is JniType.Long -> irLong(0)
                                        is JniType.Float -> IrConstImpl.float(startOffset, endOffset, type.kn, 0F)
                                        is JniType.Double -> IrConstImpl.double(startOffset, endOffset, type.kn, 0.0)
                                        is JniType.Byte -> IrConstImpl.byte(startOffset, endOffset, type.kn, 0)
                                        is JniType.BooleanAsUByte -> IrConstImpl.byte(startOffset, endOffset, type.kn, 0) // hope this works...
                                    }
                                }
                            )),
                            finallyExpression = null
                        )
                    }
                } else {
                    val suspendInvoke = context.symbols.functions(kneeInvokeJvmSuspend).single()
                    val suspendInvoker =
                        irGet(lambda.valueParameters.first { it.name == DownwardFunctionSignature.Extra.SuspendInvoker })
                    val returnType = signature.result
                    irCall(suspendInvoke.owner).apply {
                        putTypeArgument(0, returnType.encodedType.knOrNull ?: context.symbols.builtIns.unitType) // raw return type
                        putTypeArgument(1, returnType.localIrType) // actual return type
                        putValueArgument(0, irGet(environment))
                        putValueArgument(1, suspendInvoker)
                        putValueArgument(2, irLambda(context, parent, suspend = true) {
                            it.returnType = returnType.localIrType
                            with(DownwardFunctionsIr) {
                                +irReturn(irInvoke(lambda.valueParameters, source, signature, codecContext))
                            }
                        })
                        putValueArgument(3, irLambda(context, parent) {
                            it.returnType = returnType.encodedType.knOrNull ?: context.symbols.builtIns.unitType
                            it.addValueParameter("_env", environment.type)
                            it.addValueParameter("_data", returnType.localIrType)
                            // Need a new context because the local invocation might have suspended and might have returned
                            // on another thread with no current environment. This is also why we have two lambdas here, so that the
                            // new environment is provided by the runtime.
                            val freshCodecContext = IrCodecContext(
                                functionSymbol = source.symbol,
                                environment = it.valueParameters[0],
                                reverse = false,
                                logger = context.log
                            )
                            val raw = irGet(it.valueParameters[1])
                            with(DownwardFunctionsIr) {
                                +irReturn(irReceive(raw, signature, freshCodecContext))
                            }
                        })
                    }.let { call ->
                        // Technically this is useless, token is a long and needs to conversion, but leaving it
                        // for clarity and future-proofness.
                        with(DownwardFunctionsIr) {
                            irReceive(call, signature, codecContext, suspendToken = true)
                        }
                    }
                })
            }
        ))
        staticCFunctionCall
    }
    irProducts.add(property)
    initInfo.registerNative(
        context = context,
        container = signature.jniInfo.owner,
        pointerProperty = property,
        methodName = signature.jniInfo.name(includeAncestors = false).asString(),
        methodJniSignature = signature.jniInfo.signature,
    )
}
