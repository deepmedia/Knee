package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.features.KneeClass
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.codec.CodegenCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.IrCodecContext
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportAdapters
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addHandleConstructorAndField
import io.deepmedia.tools.knee.plugin.compiler.instances.InstancesCodegen.addAnyOverrides
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.decodeClass
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.encodeClass
import io.deepmedia.tools.knee.plugin.compiler.utils.asModifier
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeSpec
import io.deepmedia.tools.knee.plugin.compiler.utils.codegenFqName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.types.KotlinType

fun preprocessClass(klass: KneeClass, context: KneeContext) {
    context.mapper.register(ClassCodec(
        symbols = context.symbols,
        irClass = klass.source,
        irConstructors = klass.constructors.map { it.source.symbol },
    ))
}

fun processClass(klass: KneeClass, context: KneeContext, codegen: KneeCodegen, initInfo: InitInfo) {
    klass.makeCodegen(codegen)
    if (klass.isThrowable && klass.importInfo == null) {
        initInfo.serializableException(klass.source)
    }
    if (!context.useExport2) {
        ExportAdapters.exportIfNeeded(klass.source, context, codegen, klass.importInfo)
    }
}

/**
 * We must create a copy of the class and care about construction and destruction.
 * 1. class primary constructor must be one accepting a long reference.
 *    This also means that we must disallow constructor(Long) in native code as it would clash.
 *    Then the long term solution can be to create an inline wrapper for Long in JVM runtime.
 * 2. Add one secondary constructor per each native constructor. These constructors
 *    should call into the native constructors that return a long, and forward that to the primary.
 *    Support for this is mostly built into KneeFunction.
 * 3. Add a dispose() function that calls into the native disposer. Not much to do here
 *    because we already create a KneeFunction for it. Just make sure it gets the right name.
 */
private fun KneeClass.makeCodegen(codegen: KneeCodegen) {
    val container = codegen.prepareContainer(source, importInfo)
    codegenClone = container.addChildIfNeeded(CodegenClass(source.asTypeSpec())).apply {
        if (codegen.verbose) spec.addKdoc("knee:classes")
        spec.addModifiers(source.visibility.asModifier())
        spec.addHandleConstructorAndField(preserveSymbols = isThrowable) // for exception handling
        spec.addAnyOverrides(codegen.verbose)
        if (isThrowable) {
            spec.superclass(THROWABLE)
        }
        codegenProducts.add(this)
    }
}

class ClassCodec(
    symbols: KneeSymbols,
    private val irClass: IrClass,
    private val irConstructors: List<IrFunctionSymbol>,
) : Codec(irClass.defaultType, JniType.Long(symbols)) {

    companion object {
        fun encodedTypeForFir(module: org.jetbrains.kotlin.descriptors.ModuleDescriptor): KotlinType {
            return module.builtIns.longType
        }
        fun encodedTypeForIr(symbols: KneeSymbols): JniType {
            return JniType.Long(symbols)
        }
    }

    private val encode = symbols.functions(encodeClass).single()
    private val decode = symbols.functions(decodeClass).single()

    /**
     * This class is being returned from some function, which might be a constructor.
     * We must create a stable ref for this class so that it can be passed to the frontend.
     * In addition, if this class is owned by some other, we must add the stable ref to the owner list.
     */
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        return irCall(encode).apply {
            putValueArgument(0, irGet(local))
        }
    }

    // NOTE: in theory it is possible here to check whether this is a disposer and if it is,
    // release the stable refs here.
    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        return irCall(decode).apply {
            putTypeArgument(0, localIrType)
            putValueArgument(0, irGet(jni))
        }
    }

    /**
     * A long reference was returned by native code. Here we must call the constructor of our class
     * accepting the reference. If this is a constructor, we should instead call this(knee = $bridge),
     * which means returning bridge value with no edits.
     */
    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        val isConstructor = codegenContext.functionSymbol in irConstructors
        return when {
            isConstructor -> jni
            else -> "${irClass.codegenFqName}(`${InstancesCodegen.HandleField}` = $jni)"
        }
    }

    /**
     * A JVM class must reach the native world. This means that we must pass the native reference instead.
     */
    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        return "$local.`${InstancesCodegen.HandleField}`"
    }
}