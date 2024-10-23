package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.instances.InterfaceNames.asInterfaceName
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeInterface
import io.deepmedia.tools.knee.plugin.compiler.functions.UpwardFunctionSignature
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.context.KneeOrigin
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.export.v1.hasExport1Flag
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportAdapters
import io.deepmedia.tools.knee.plugin.compiler.features.KneeUpwardProperty
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.import.concrete
import io.deepmedia.tools.knee.plugin.compiler.import.writableParent
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addHandleConstructorAndField
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addAnyOverrides
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeInterface
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeInterface
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.JvmInterfaceWrapper
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findTypeAliasAcrossModuleDependencies
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.types.KotlinType

fun preprocessInterface(interface_: KneeInterface, context: KneeContext) {
    context.log.logMessage("preprocessInterface(${interface_.source.name}), owned = ${interface_.source.isPartOf(context.module)}")
    interface_.makeIrImplementation(context)
    context.mapper.register(InterfaceCodec(
        context = context,
        interfaceClass = interface_.source,
        interfaceImplClass = interface_.irImplementation,
        importInfo = interface_.importInfo
    ))
}

fun processInterface(interface_: KneeInterface, context: KneeContext, codegen: KneeCodegen, initInfo: InitInfo) {
    context.log.logMessage("processInterface(${interface_.source.name}), owned = ${interface_.source.isPartOf(context.module)}")
    if (interface_.source.isPartOf(context.module)) {
        interface_.makeCodegenClone(codegen)
    }
    interface_.makeCodegenImplementation(codegen, context)
    interface_.makeIrImplementationContents(context)
    // Generics should not matter here because we just findClass() the FQN
    initInfo.preload(listOf(
        interface_.source.defaultType,
        interface_.irImplementation.defaultType
    ))

    run {

        // Trick so that we don't have to pass the dispatch receiver from the function we are building.
        // This is not 100% safe, it would probably fail in nested scopes e.g. inside irLambda.
        fun IrBuilderWithScope.irThis() = irGet((scope.scopeOwnerSymbol as IrSimpleFunctionSymbol).owner.dispatchReceiverParameter!!)

        val utilitySuperClass = context.symbols.klass(JvmInterfaceWrapper).owner
        val virtualMachine = utilitySuperClass.findDeclaration<IrProperty> { it.name.asString() == "virtualMachine" }!!.getter!!
        val methodOwner = utilitySuperClass.findDeclaration<IrProperty> { it.name.asString() == "methodOwnerClass" }!!.getter!!
        val jvmInterfaceObject = utilitySuperClass.findDeclaration<IrProperty> { it.name.asString() == "jvmInterfaceObject" }!!.getter!!
        val methodFromSignature = utilitySuperClass.findDeclaration<IrSimpleFunction> { it.name.asString() == "method" }!!

        interface_.irGetVirtualMachine = { irCall(virtualMachine).apply { dispatchReceiver = irThis() }}
        interface_.irGetMethodOwner = { irCall(methodOwner).apply { dispatchReceiver = irThis() }}
        interface_.irGetJvmObject = { irCall(jvmInterfaceObject).apply { dispatchReceiver = irThis() }}
        interface_.irGetMethod = { signature -> irCall(methodFromSignature).apply {
            dispatchReceiver = irThis()
            putValueArgument(0, irString(signature.jniInfo.name(false).asString() + "::" + signature.jniInfo.signature))
        }}
    }

    if (!context.useExport2) {
        ExportAdapters.exportIfNeeded(interface_.source, context, codegen, interface_.importInfo)
    }
}

/**
 * Given Foo interface, create Foo interface in JVM.
 * Note that for local imports with generics, we make the clone generic too, e.g. Flow<T>.
 * - That doesn't mean that a generic Flow<T> can cross JNI. The codec still refers to Flow<ConcreteType>.
 * - If user imports Flow<A> and Flow<B>, we don't want to write the codegen clone twice.
 *   We use addChildIfNeeded for this.
 */
private fun KneeInterface.makeCodegenClone(codegen: KneeCodegen) {
    val container = codegen.prepareContainer(source, importInfo)
    val builder = when {
        source.isFun -> TypeSpec.funInterfaceBuilder(source.name.asString())
        else -> TypeSpec.interfaceBuilder(source.name.asString())
    }.apply {
        if (codegen.verbose) addKdoc("knee:interfaces:clone")
        addModifiers(source.visibility.asModifier())
        addTypeVariables(importInfo?.typeVariables ?: emptyList())
    }
    codegenClone = container.addChildIfNeeded(CodegenClass(builder)).apply {
        codegenProducts.add(this)
    }
}

/**
 * Given Foo interface, create FooImpl in JVM
 * Single constructor accepting the Long stable ref.
 */
private fun KneeInterface.makeCodegenImplementation(codegen: KneeCodegen, context: KneeContext) {
    val name = source.codegenName.asInterfaceName(importInfo).asString()
    val exported1 = !context.useExport2 && source.hasExport1Flag
    val container = codegen.prepareContainer(source, importInfo)
    val builder = TypeSpec.classBuilder(name).apply {
        when {
            exported1 -> addModifiers(KModifier.PUBLIC)
            container is CodegenClass && container.isInterface -> {} // Can't put internal inside an interface...
            else -> addModifiers(KModifier.INTERNAL)
        }
        if (codegen.verbose) addKdoc("knee:interfaces:impl")
        addSuperinterface(source.defaultType.concrete(importInfo).asTypeName())
        addHandleConstructorAndField(false)
        addAnyOverrides(codegen.verbose)
    }
    codegenImplementation = CodegenClass(builder).apply {
        container.addChild(this)
        codegenProducts.add(this)
    }
}

/**
 * Given Foo interface, create FooImpl in KN
 * It should extend the utility class JvmInterfaceWrapper<T> provided by the runtime.
 */
private fun KneeInterface.makeIrImplementation(context: KneeContext) {
    val container = source.writableParent(context, importInfo) as IrDeclarationContainer
    val sourceConcreteType = source.defaultType.concrete(importInfo)
    val superClass = context.symbols.klass(JvmInterfaceWrapper).owner
    val wrapperClass = context.factory.buildClass {
        this.modality = Modality.FINAL
        this.origin = if (importInfo != null) KneeOrigin.KNEE_IMPORT_PARENT else KneeOrigin.KNEE
        this.visibility = DescriptorVisibilities.INTERNAL
        this.name = source.name.asInterfaceName(importInfo)
    }.also { wrapperClass ->
        wrapperClass.parent = container
        wrapperClass.superTypes = listOf(sourceConcreteType, superClass.typeWith(sourceConcreteType))
        wrapperClass.createParameterDeclarations() // <this> receiver
    }
    container.addChild(wrapperClass)
    irProducts.add(wrapperClass)
    irImplementation = wrapperClass
}

private fun KneeInterface.makeIrImplementationContents(context: KneeContext) {
    val sourceConcreteType = source.defaultType.concrete(importInfo)
    val superClass = context.symbols.klass(JvmInterfaceWrapper).owner
    irImplementation.addConstructor {
        this.origin = KneeOrigin.KNEE
        this.isPrimary = true
    }.let { constructor ->
        val superConstructor = superClass.primaryConstructor!!
        constructor.valueParameters += superConstructor.valueParameters[0].copyTo(constructor, defaultValue = null) // 0: JniEnvironment
        constructor.valueParameters += superConstructor.valueParameters[1].copyTo(constructor, defaultValue = null) // 1: jobject
        constructor.body = with(DeclarationIrBuilder(context.plugin, constructor.symbol)) {
            irBlockBody {
                context.log.injectLog(this, "Calling super constructor")
                // context.log.injectLog(this, CodegenType.from(sourceConcreteType).jvmClassName)
                // context.log.injectLog(this, CodegenType.from(irImplementation.defaultType).jvmClassName)

                +irDelegatingConstructorCall(superConstructor).apply {
                    putValueArgument(0, irGet(constructor.valueParameters[0]))
                    putValueArgument(1, irGet(constructor.valueParameters[1]))
                    // Class FQNs will be passed to jni.findClass, so handle dollar sign and codegen renames correctly
                    putValueArgument(2, irString(CodegenType.from(sourceConcreteType).jvmClassName))
                    putValueArgument(3, irString(CodegenType.from(irImplementation.defaultType).jvmClassName))
                    // Name and signature of the companion object function, alternated
                    val allExportedFunctions = upwardFunctions +
                            upwardProperties.mapNotNull(KneeUpwardProperty::setter) +
                            upwardProperties.map(KneeUpwardProperty::getter)
                    putValueArgument(4, irVararg(
                        elementType = context.symbols.builtIns.stringType,
                        values = allExportedFunctions.flatMap {
                            val signature = UpwardFunctionSignature(it.source, it.kind, context.symbols, context.mapper)
                            listOf(
                                irString(signature.jniInfo.name(false).asString()),
                                irString(signature.jniInfo.signature)
                            )
                        }
                    ))
                }
                context.log.injectLog(this, "Called super constructor, init self")
                +IrInstanceInitializerCallImpl(startOffset, endOffset, irImplementation.symbol, context.symbols.builtIns.unitType)
            }
        }
    }
}


class InterfaceCodec(
    private val context: KneeContext,
    interfaceClass: IrClass,
    private val interfaceImplClass: IrClass,
    importInfo: ImportInfo?
) : Codec(
    /**
     * NOTE: generics (through importInfo) might have a different JVM representation!
     * Say, io.deepmedia.knee.buffer.ByteBuffer in native and java.nio.ByteBuffer in JVM
     * in the function @Knee fun foo(cb: (ByteBuffer) -> Unit).
     * In this case, we are printing the interface with wrong subtypes in JVM codegen.
     *
     * For now, we fix this by adding type aliases for the buffer case. Not sure about a
     * proper solution (CPointer + KneeRaw should have the same problem).
     * TODO: Maybe CodegenType.from(localType) should optionally inspect mappers.
     */
    localType = interfaceClass.defaultType.concrete(importInfo),
    encodedType = JniType.Object(context.symbols, CodegenType.from(ANY))
) {

    companion object {
        fun encodedTypeForFir(module: ModuleDescriptor): KotlinType {
            val descr = module.findTypeAliasAcrossModuleDependencies(CInteropIds.COpaquePointer)!!
            return descr.expandedType
            // return KotlinTypeFactory.simpleNotNullType(TypeAttributes.Empty, descr, emptyList())
        }

        fun encodedTypeForIr(symbols: KneeSymbols): JniType {
            return JniType.Object(symbols, CodegenType.from(ANY))
        }
    }

    override fun toString() = "InterfaceCodec"

    private val encode = context.symbols.functions(encodeInterface).single()
    private val decode = context.symbols.functions(decodeInterface).single()

    /**
     * KN: Some interface is going to JVM.
     * - if interface was originally JVM, return JVM!
     * - otherwise create a KN StableRef and return it as encoded long
     */
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(encode).apply {
            putTypeArgument(0, localIrType)
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(local))
        }
    }

    /**
     * KN: Some interface is coming from JVM.
     * - if interface is a long, it's a StableRef address
     * - otherwise it's a jobject with a reference to a JVM interface.
     *   In this case we should create a FooImpl instance using the generated impl class.
     */
    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        val logPrefix = "InterfaceCodec(${localCodegenType.name.simpleName})"
        irContext.logger.injectLog(this, "$logPrefix DECODING")
        return irCall(decode).apply {
            putTypeArgument(0, localIrType)
            putValueArgument(0, irGet(irContext.environment))
            putValueArgument(1, irGet(jni))
            putValueArgument(2, irLambda(
                context = this@InterfaceCodec.context,
                parent = parent,
                valueParameters = emptyList(),
                returnType = interfaceImplClass.defaultType,
                content = {
                    irContext.logger.injectLog(this, "$logPrefix INSTANTIATING the implementation class")
                    // irContext.logger.injectLog(this, irContext.environment)
                    // irContext.logger.injectLog(this, jni)
                    +irReturn(irCallConstructor(interfaceImplClass.primaryConstructor!!.symbol, emptyList()).apply {
                        putValueArgument(0, irGet(irContext.environment)) // environment
                        putValueArgument(1, irGet(jni)) // jobject
                    })
                }
            ))
        }
    }

    /**
     * JVM: some interface implementation arrived from KN in form of kotlin.Any. It could be
     * - A boxed java.lang.Long pointing to a stable ref address, which can be used for delegation
     *   In this case we should create an instance of "KneeFoo" passing the address to the constructor
     * - An actual interface. This happens if the interface was originally created in Java.
     */
    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        val fqn = localCodegenType.name
        val impl = interfaceImplClass.defaultType.asTypeName()
        /* val impl = fqn
            .copy(simpleName = fqn.simpleName.asInterfaceName(importInfo))
            .copy(clearGenerics = true)
            .copy(packageName = remapBasedOnWritablePackage()) */
        addStatement("val ${jni}_: %T =", fqn)
        withIndent {
            addStatement("if ($jni is %T) $jni as %T", fqn.copy(wildcardGenerics = true), fqn)
            addStatement("else %T($jni as Long)", impl)
        }
        return "${jni}_"
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        // Special case during JVM to KN functions when the interface is the receiver.
        // It's not fundamental but avoids some warnings in generated code (this is Type where this is obviously type)
        if (local == "this") return "$local.`${InstancesCodegen.HandleField}`"

        val impl = interfaceImplClass.defaultType.asTypeName()
        addStatement("val ${local}_: Any = ($local as? %T)?.`${InstancesCodegen.HandleField}` ?: $local", impl)
        return "${local}_"
    }
}