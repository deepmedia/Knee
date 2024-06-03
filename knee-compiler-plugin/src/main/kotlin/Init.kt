package io.deepmedia.tools.knee.plugin.compiler

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.export.v2.ExportAdapters2
import io.deepmedia.tools.knee.plugin.compiler.export.v2.ExportedTypeInfo
import io.deepmedia.tools.knee.plugin.compiler.features.KneeInitializer
import io.deepmedia.tools.knee.plugin.compiler.features.KneeModule
import io.deepmedia.tools.knee.plugin.compiler.metadata.ModuleMetadata
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.JNINativeMethod
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid


private val KneeSymbols.moduleClass get() = klass(RuntimeIds.KneeModule).owner
private val KneeSymbols.modulePublicConstructor get() = moduleClass.constructors.first { !it.isPrimary }

// private val KneeSymbols.moduleBuilderClass get() = klass(Names.runtimeKneeModuleBuilderClass).owner
private val KneeSymbols.moduleBuilderExportFunction get() = functions(RuntimeIds.KneeModuleBuilder_export).single()
private val KneeSymbols.moduleBuilderExportAdapterFunction get() = functions(RuntimeIds.KneeModuleBuilder_exportAdapter).single()

fun processInit(
    context: KneeContext,
    codegen: KneeCodegen,
    info: InitInfo,
) {
    when (info) {
        is InitInfo.Module -> {
            // There should be only one module, but if for some reason many were provided, process all of them
            // Goal: replace the module public constructor with the private one, passing more data to it
            // Note that since this is a module, we may also have to deal with exports (while initKnee() apps can't export)
            info.modules.forEach { module ->
                // This was used to parse IrClass-es from metadata, which had a vararg as first parameter
                /* val dependencyTypes: List<IrType> = metadataAnnotation.getValueArgument(0).varargElements<IrClassReference>().map { it.classType }
                val dependencyExpressions: List<IrExpression> = with(builder) { dependencyTypes.map { irGetObject(it.classOrFail) } } */

                // A few IR things to do:
                // 1. replace super constructor KneeModule(...) with our own constructor (which passes more data, e.g. preloads)
                // 2. collect export()-ed types and determine their info
                // 3. replace export<Type>() calls with exportAdapter<EncodedType, Type>(adapter)
                // 4. write dependency information in the module metadata (via annotation)
                // https://github.com/androidx/androidx/blob/fec3b387ce47bad7682d01042c22d1913268c2bc/compose/compiler/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/lower/ComposerIntrinsicTransformer.kt#L62
                val exportedTypes = mutableListOf<ExportedTypeInfo>()
                val dependencyModules = module.collectDependencies() // do before transforming the super constructor!
                module.source.transformChildrenVoid(object : IrElementTransformerVoid() {

                    // Grab the superclass constructor call. Will call visitDelegatingConstructorCall
                    override fun visitConstructor(declaration: IrConstructor): IrStatement {
                        declaration.body!!.transformChildrenVoid(this)
                        return super.visitConstructor(declaration)
                    }

                    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                        val isPublicConstructor = expression.symbol == context.symbols.modulePublicConstructor.symbol
                        if (isPublicConstructor) {
                            val builder = DeclarationIrBuilder(context.plugin, expression.symbol)
                            return builder.irCreateModule(
                                isSuperclass = true,
                                symbols = context.symbols,
                                initInfo = info,
                                varargDependencies = expression.getValueArgument(0),
                                builderBlock = expression.getValueArgument(1)
                            )
                        }
                        return super.visitDelegatingConstructorCall(expression)
                    }

                    override fun visitCall(expression: IrCall): IrExpression {
                        if (context.useExport2 && expression.symbol == context.symbols.moduleBuilderExportFunction) {
                            val exportedType = expression.getTypeArgument(0)!!.simple("export<Type>()")
                            val exportedTypeInfo = ExportedTypeInfo(exportedTypes.size, context.mapper.get(exportedType))
                            exportedTypes.add(exportedTypeInfo)
                            val builder = DeclarationIrBuilder(context.plugin, module.source.symbol)
                            val replacement = builder.irExportAdapter(context, expression.dispatchReceiver!!, exportedTypeInfo)
                            //println("ORIGINAL MODULE_BUILDER_EXPORT = ${expression.dumpKotlinLike()}")
                            //println("REPLACEMENT MODULE_BUILDER_EXPORT = ${replacement.dumpKotlinLike()}")
                            return replacement
                        }
                        return super.visitCall(expression)
                    }
                })

                // Write useful information into the module metadata annotation
                val metadata = ModuleMetadata(exportedTypes, dependencyModules)
                metadata.write(module.source, context)

                // A few JVM things to do:
                // 1. Create codegen module extending KneeModule
                // 2. Create codegen adapters and pass them to the module
                codegen.makeCodegenModule(module, context, exportedTypes)
            }
        }
        is InitInfo.Initializer -> {
            // It's possible to have multiple initKnee() call, for example in a if-else branch.
            // We don't care, process all of them and inject a synthetic module
            info.initializers.forEach { initializer ->
                // Goal: replace initKnee(ENV, dep1, dep2, dep3, ...) with initKnee(ENV, SyntheticModule(dep1, dep2, dep3))
                // TODO: it is wrong to pass the expression symbol, it represents the initKnee() function in runtime module
                val builder = DeclarationIrBuilder(context.plugin, initializer.expression.symbol)
                val dependencies = initializer.expression.getValueArgument(1)
                initializer.expression.putValueArgument(1, builder.irVararg(
                    elementType = context.symbols.moduleClass.defaultType,
                    values = listOf(builder.irCreateModule(
                        isSuperclass = false,
                        symbols = context.symbols,
                        initInfo = info,
                        varargDependencies = dependencies,
                        builderBlock = null
                    ))
                ))
            }
        }
    }
}


sealed class InitInfo {
    class Module(val modules: List<KneeModule>) : InitInfo()
    class Initializer(val initializers: List<KneeInitializer>) : InitInfo()

    fun dependencies(json: Json) = when (this) {
        is Module -> modules.flatMap { it.collectDependencies() }
        is Initializer -> initializers.flatMap { it.collectDependencies() }
    }.associateWith { ModuleMetadata.read(it, json) }

    val preloads = mutableSetOf<IrSimpleType>()
    fun preload(types: Collection<IrSimpleType>) {
        preloads.addAll(types)
    }

    val serializableExceptions = mutableSetOf<IrClass>()
    fun serializableException(klass: IrClass) {
        serializableExceptions.add(klass) // can't be exported
    }

    val registerNativesEntries = mutableListOf<RegisterNativesEntry>()
    fun registerNative(
        context: KneeContext,
        container: CodegenType,
        pointerProperty: IrProperty,
        methodName: String,
        methodJniSignature: String
    ) {
        context.log.logMessage("registerNative: adding $methodName ($methodJniSignature) in ${container.jvmClassName}")
        registerNativesEntries.add(RegisterNativesEntry(container, pointerProperty, methodName, methodJniSignature))
    }

    data class RegisterNativesEntry(
        /**
         * The class containing this JVM method. It's the parent class
         * or the synthetic <filename>Kt in case of top level functions.
         */
        val container: CodegenType,
        /**
         * A property returning a static c pointer to the native function.
         */
        val pointerProperty: IrProperty,

        val methodName: String,
        val methodJniSignature: String,
    )
}

/**
 * Returns either a delegating constructor call or a pure constructor call,
 * depending on the [isSuperclass] flag.
 */
private fun DeclarationIrBuilder.irCreateModule(
    isSuperclass: Boolean,
    symbols: KneeSymbols,
    initInfo: InitInfo,
    varargDependencies: IrExpression?,
    builderBlock: IrExpression?
): IrExpression {
    val constructor = symbols.moduleClass.constructors.first { it.isPrimary }
    val constructorCall = when {
        isSuperclass -> irDelegatingConstructorCall(constructor)
        else -> irCallConstructor(constructor.symbol, emptyList())
    }
    constructorCall.apply {
        // val registerNativeContainers: List<String>
        // val registerNativeMethods: List<List<JniNativeMethod>>
        val groups = initInfo.registerNativesEntries.groupBy { it.container }.entries.map { it }
        putValueArgument(0, irRegisterNativesContainers(symbols, groups.map { it.key }))
        putValueArgument(1, irRegisterNativesMethods(symbols, groups.map { it.value }))

        // val preloadFqns: List<String>
        putValueArgument(2, irPreloadFqns(symbols, initInfo.preloads))

        // val exceptions: List<SerializableException>
        putValueArgument(3, irSerializableExceptions(symbols, initInfo.serializableExceptions))

        // val dependencies: List<KneeModule>
        // val block: (KneeModuleBuilder.() -> Unit)?
        val dependencies = varargDependencies.varargElements<IrExpression>()
        putValueArgument(4, irListOf(symbols, symbols.moduleClass.defaultType, dependencies))
        putValueArgument(5, builderBlock ?: irNull())

        // val dependencies: Array<out KneeModule>?
        // val block: (KneeModuleBuilder.() -> Unit)?
        // Note that being a vararg, the expression can actually be null
        // putValueArgument(3, dependencies ?: irNull())
        // putValueArgument(4, builderBlock ?: irNull())
    }
    return constructorCall
}

private fun DeclarationIrBuilder.irListOf(symbols: KneeSymbols, type: IrType, contents: List<IrExpression>): IrExpression {
    val listOf = symbols.functions(KotlinIds.listOf).single { it.owner.valueParameters.singleOrNull()?.isVararg == true }
    return irCall(listOf).apply {
        putTypeArgument(0, type)
        putValueArgument(0, irVararg(type, contents))
    }
}

private fun DeclarationIrBuilder.irPreloadFqns(symbols: KneeSymbols, preloads: Set<IrSimpleType>): IrExpression {
    return irListOf(symbols, symbols.builtIns.stringType, preloads.map {
        irString(CodegenType.from(it).jvmClassName)
    })
}

private fun DeclarationIrBuilder.irSerializableExceptions(symbols: KneeSymbols, classes: Set<IrClass>): IrExpression {
    val type = symbols.klass(RuntimeIds.SerializableException)
    return irListOf(symbols, type.defaultType, classes.map {
        irCallConstructor(type.constructors.single(), emptyList()).apply {
            putValueArgument(0, irString(it.classIdOrFail.asFqNameString())) // nativeFqn: String
            putValueArgument(1, irString(CodegenType.from(it.defaultType).jvmClassName)) // jvmFqn: String
        }
    })
}

private fun DeclarationIrBuilder.irRegisterNativesContainers(symbols: KneeSymbols, containers: List<CodegenType>): IrExpression {
    return irListOf(symbols, symbols.builtIns.stringType, containers.map { irString(it.jvmClassName) })
}

private fun DeclarationIrBuilder.irRegisterNativesMethods(symbols: KneeSymbols, entriesLists: List<List<InitInfo.RegisterNativesEntry>>): IrExpression {
    val methodClass = symbols.klass(JNINativeMethod)
    val methodConstructor = methodClass.constructors.single()
    val listOfMethods = symbols.builtIns.listClass.typeWith(methodClass.defaultType)
    return irListOf(symbols,
        type = symbols.builtIns.listClass.typeWith(listOfMethods),
        contents = entriesLists.map { entries ->
            irListOf(symbols,
                type = listOfMethods,
                contents = entries.map { entry ->
                    irCallConstructor(methodConstructor, emptyList()).apply {
                        putValueArgument(0, irString(entry.methodName))
                        putValueArgument(1, irString(entry.methodJniSignature))
                        putValueArgument(2, irCall(entry.pointerProperty.getter!!))
                    }
                }
            )
        }
    )
}

private fun DeclarationIrBuilder.irExportAdapter(
    context: KneeContext,
    moduleBuilderInstance: IrExpression,
    exportedType: ExportedTypeInfo
): IrExpression {
    val function = context.symbols.moduleBuilderExportAdapterFunction
    return irCall(function).apply {
        dispatchReceiver = moduleBuilderInstance
        putTypeArgument(0, exportedType.encodedType.kn)
        putTypeArgument(1, exportedType.localIrType)
        // dispatchReceiver = irGet(function.owner.parentAsClass.thisReceiver!!)
        putValueArgument(0, irInt(exportedType.id))
        putValueArgument(1, with(ExportAdapters2) { irCreateExportAdapter(exportedType, context) })
    }
}

/**
 * Modules are created as object MyModule : KneeModule(varargDependencies, otherStuff)
 * We need to intercept the delegating constructor call.
 */
private fun KneeModule.collectDependencies(): List<IrClass> {
    var expr: IrExpression? = null
    source.constructors.single().body!!.acceptChildrenVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }
        override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall) {
            check(expression.symbol.owner.constructedClass.classId == RuntimeIds.KneeModule) { "Wrong delegating constructor call: ${expression.dumpKotlinLike()}" }
            check(expr == null) { "Found two delegating constructor call: ${expr}, ${expression}"}
            expr = expression.getValueArgument(0)
            super.visitDelegatingConstructorCall(expression)
        }
    })
    return expr.varargElements<IrGetObjectValue>().map { it.symbol.owner }
}

/**
 * Initializers are invoked as initKnee(environment, varargDependencies)
 * We just need to retrieve and unwrap the second argument..
 */
private fun KneeInitializer.collectDependencies(): List<IrClass> {
    return expression.getValueArgument(1).varargElements<IrGetObjectValue>().map { it.symbol.owner }
}

/**
 * Vararg expressions can sometimes be null, if no items were provided.
 */
private inline fun <reified T: IrExpression> IrExpression?.varargElements(): List<T> {
    if (this == null) return emptyList()
    return (this as IrVararg).elements.map { it as? T ?: error("Vararg elements should be ${T::class}, was ${it::class}") }
}

private fun KneeCodegen.makeCodegenModule(module: KneeModule, context: KneeContext, exportedTypes: List<ExportedTypeInfo>) {
    val name = module.source.name.asString()
    val container = prepareContainer(module.source, null)
    val moduleClass: ClassName = context.symbols.klass(RuntimeIds.KneeModule).owner.defaultType.asTypeName() as ClassName
    val adapterClass: ClassName = moduleClass.nestedClass("Adapter")
    val builder = TypeSpec.objectBuilder(name)
        .addModifiers(KModifier.PUBLIC)
        .let { if (verbose) it.addKdoc("knee:init") else it }
        .superclass(context.symbols.klass(RuntimeIds.KneeModule).owner.defaultType.asTypeName())
        .addProperty(
            PropertySpec.builder("exportAdapters", MAP.parameterizedBy(INT, adapterClass.parameterizedBy(STAR, STAR)), KModifier.OVERRIDE)
                .initializer(CodeBlock.builder()
                    .addStatement("mapOf(")
                    .withIndent {
                        for (exportedType in exportedTypes) {
                            add("${exportedType.id} to ")
                            add(CodeBlock.builder().apply {
                                with(ExportAdapters2) { codegenCreateExportAdapter(exportedType, context) }
                            }.build())
                            add(", ")
                        }
                    }
                    .addStatement(")")
                    .build()
                )
                .build()
        )
    container.addChild(CodegenClass(builder))
}

