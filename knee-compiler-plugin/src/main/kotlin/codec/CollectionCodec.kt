package io.deepmedia.tools.knee.plugin.compiler.codec

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.JObjectCollectionCodec
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.PrimitiveArraySpec
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.PrimitiveCollectionCodec
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.TransformingCollectionCodec
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.typedArraySpec
import io.deepmedia.tools.knee.plugin.compiler.utils.irLambda
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.primaryConstructor

fun Codec.withCollectionCodecs(
    context: KneeContext,
    vararg kinds: CollectionKind = CollectionKind.entries.toTypedArray()
): Array<Codec> = listOf(
    this, *collectionCodecs(context, *kinds)
).toTypedArray()

fun Codec.collectionCodecs(
    context: KneeContext,
    vararg kinds: CollectionKind = CollectionKind.entries.toTypedArray()
): Array<Codec> {
    return kinds.map { kind -> CollectionCodec(context, this.wrappable(), kind) }.toTypedArray()
}

/**
 * A pretty complex codec that wraps an element codec to provide collection support.
 * We support different kinds of collections, see [CollectionKind].
 *
 * For example, given a [StringCodec] which has:
 * - local ir type is kotlin.String         [StringCodec.localIrType]
 * - jni type is jobject <-> kotlin.String  [StringCodec.encodedType]
 * - local codegen type is kotlin.String    [StringCodec.localCodegenType]
 *
 * First of all, the jni representation of a collection of strings is [JniType.Array].
 * This is determined automatically by [JniType.Object.array].
 *
 * Then the mapper must decode a jobjectArray into a List/Set/Array/Sequence of strings. This
 * is done by leveraging runtime utilities called codecs. Codec expose functions with the List/Set/Array/Sequence
 * name in it, which we can fetch at compile time here in the plugin.
 *
 * By default, codecs respect the inner type, so a jobjectArray can become a List<jobject>, Set<jobject> and so on.
 * This is not what we want because the element codec might be mapping between different types,
 * like in our example jobject <==> kotlin.String .
 *
 * For this reason, a special codec called TransformingCollectionCodec exists which takes two lambdas for
 * encoding and decoding the object. This codec will implement the lambdas by delegating them
 * to the wrapped codec, so that jobject is transformed to kotlin.String and viceversa.
 */

// TODO: revisit - when elementCodec does transform, we create a new instance of transforming helper at every encode decode!
// TODO:           also for a function say foo(List<Foo>): List<Foo>, we create it twice, one for the param and one for return
// TODO: wrap in KneeMapper instead of using withCollectionCodecs()
class CollectionCodec constructor(
    private val context: KneeContext,
    private val elementCodec: Codec,
    private val collectionKind: CollectionKind
) : Codec(
    localIrType = collectionKind.getCollectionTypeOf(elementCodec.localIrType, context.symbols),
    localCodegenType = collectionKind.getCollectionTypeOf(elementCodec.localCodegenType, context.symbols),
    encodedType = when (val type = elementCodec.encodedType) {
        is JniType.Primitive -> type.array(context.symbols)
        is JniType.Object -> type.array(context.symbols)
        else -> error("Unsupported element type: $type")
    }
) {
    /**
     * The inner codec is the one that transforms the jobjectArray in a Collection<jobject>.
     * We have two different implementations based on whether the encoded type is a primitive or not.
     */
    private val runtimeHelperClassRaw: IrClass = when (val type = elementCodec.encodedType) {
        is JniType.Primitive -> context.symbols.klass(PrimitiveCollectionCodec(type.knSimpleName)).owner
        is JniType.Object -> context.symbols.klass(JObjectCollectionCodec).owner
        else -> error("Not possible")
    }

    /**
     * The outer codec wraps the [runtimeHelperClassRaw] (if needed) to transform the inner element type.
     * For example, it will transform a Collection<jobject> into a Collection<jstring>.
     */
    private val runtimeHelperClass: IrClass = when {
        elementCodec.needsIrConversion -> context.symbols.klass(TransformingCollectionCodec).owner
        else -> runtimeHelperClassRaw
    }

    private fun IrBuilderWithScope.irGetOrCreateHelperRaw(): IrDeclarationReference {
        return when (val type = elementCodec.encodedType) {
            is JniType.Primitive -> irGetObject(runtimeHelperClassRaw.symbol)
            is JniType.Object -> irCallConstructor(runtimeHelperClassRaw.primaryConstructor!!.symbol, emptyList()).apply {
                putValueArgument(0, irString(type.jvm.jvmClassName))
            }
            else -> error("Should not happen")
        }
    }

    private fun IrBuilderWithScope.irGetOrCreateHelper(codecContext: IrCodecContext): IrDeclarationReference {
        if (!elementCodec.needsIrConversion) return irGetOrCreateHelperRaw()

        // We're creating a transforming codec.
        val rawHelper = irGetOrCreateHelperRaw()
        return irCallConstructor(runtimeHelperClass.primaryConstructor!!.symbol, listOf(
            // Type arguments: Source (e.g. jobject), Transformed (e.g. String), TransformedArrayType (e.g. Array<String>)
            elementCodec.encodedType.knOrNull!!,
            elementCodec.localIrType,
            CollectionKind.Array.getCollectionTypeOf(elementCodec.localIrType, this@CollectionCodec.context.symbols)
        )).apply {
            // Constructor param: CollectionCodec<Encoded, *>
            putValueArgument(0, rawHelper)
            // Constructor param: ArraySpec<DecodedArrayType, Decoded>
            // Return type of this is symbols.klass(runtimeArraySpecClass)
            // .typeWith(CollectionKind.Array.getCollectionType(elementCodec.localType, symbols), elementCodec.localType)
            putValueArgument(1, when (val type = elementCodec.encodedType) {
                is JniType.Primitive -> {
                    val name = PrimitiveArraySpec(type.jvmSimpleName)
                    irGetObject(this@CollectionCodec.context.symbols.klass(name))
                }
                is JniType.Object -> {
                    val name = typedArraySpec
                    irCall(this@CollectionCodec.context.symbols.functions(name).single()).apply {
                        putTypeArgument(0, type.kn)
                    }
                }
                else -> error("Not possible")
            })
            // Constructor param: Source --> Transformed decoding lambda
            putValueArgument(2, irLambda(
                context = this@CollectionCodec.context,
                parent = this@irGetOrCreateHelper.parent,
                valueParameters = listOf(elementCodec.encodedType.knOrNull!!),
                returnType = elementCodec.localIrType,
                content = { lambda ->
                    +irReturn(with(elementCodec) { irDecode(codecContext, lambda.valueParameters[0]) })
                }
            ))
            // Constructor param: Transformed --> Source encoding lambda
            putValueArgument(3, irLambda(
                context = this@CollectionCodec.context,
                parent = this@irGetOrCreateHelper.parent,
                valueParameters = listOf(elementCodec.localIrType),
                returnType = elementCodec.encodedType.knOrNull!!,
                content = { lambda ->
                    +irReturn(with(elementCodec) { irEncode(codecContext, lambda.valueParameters[0]) })
                }
            ))

        }
    }

    // jobjectArray -> Collection<String>
    override fun IrStatementsBuilder<*>.irDecode(irContext: IrCodecContext, jni: IrValueDeclaration): IrExpression {
        val codec = irTemporary(irGetOrCreateHelper(irContext), "helper")
        val decode = runtimeHelperClass.functions.single { it.name.asString() == "decodeInto${collectionKind.name}" }
        return irCall(decode).apply {
            dispatchReceiver = irGet(codec)
            extensionReceiver = irGet(irContext.environment)
            putValueArgument(0, irGet(jni))
        }
    }

    // Collection<String> -> jobjectArray
    override fun IrStatementsBuilder<*>.irEncode(irContext: IrCodecContext, local: IrValueDeclaration): IrExpression {
        val codec = irTemporary(irGetOrCreateHelper(irContext), "helper")
        val encode = runtimeHelperClass.functions.single { it.name.asString() == "encode${collectionKind.name}" }
        return irCall(encode).apply {
            dispatchReceiver = irGet(codec)
            extensionReceiver = irGet(irContext.environment)
            putValueArgument(0, irGet(local))
        }
    }

    private fun String.toCollectionKind(arrayName: String, old: CollectionKind, new: CollectionKind): String {
        return when (new) {
            old -> this
            CollectionKind.Set -> "${this}.toSet()"
            CollectionKind.List -> "${this}.toList()"
            CollectionKind.Array -> {
                when (old) {
                    CollectionKind.Set -> "${this}.to${arrayName}Array()"
                    CollectionKind.List -> "${this}.to${arrayName}Array()"
                    else -> error("Can't happen.")
                }
            }
        }
    }

    override fun CodeBlock.Builder.codegenDecode(codegenContext: CodegenCodecContext, jni: String): String {
        val arrayName = when (elementCodec.localCodegenType.name) {
            INT -> "Int"
            BYTE -> "Byte"
            BOOLEAN -> "Boolean"
            CHAR -> "Char"
            SHORT -> "Short"
            LONG -> "Long"
            FLOAT -> "Float"
            DOUBLE -> "Double"
            else -> "Typed"
        }

        // We always receive an array from JNI, but we might have to map the individual elements
        // to a different type, in which case the type will have to change to list.
        return when (elementCodec.needsCodegenConversion) {
            false -> jni.toCollectionKind(arrayName, CollectionKind.Array, collectionKind)
            else -> {
                val elementMapper = CodeBlock.builder().also { block ->
                    val res = with(elementCodec) { block.codegenDecode(codegenContext, "it") }
                    block.addStatement(res)
                }
                "$jni.map { ${elementMapper.build()} }".toCollectionKind(arrayName, CollectionKind.List, collectionKind)
            }
        }
    }

    override fun CodeBlock.Builder.codegenEncode(codegenContext: CodegenCodecContext, local: String): String {
        val arrayName = when (val type = elementCodec.encodedType) {
            is JniType.Primitive -> type.jvmSimpleName
            is JniType.Object -> "Typed"
            else -> error("Not possible")
        }

        return when (elementCodec.needsCodegenConversion) {
            false -> local.toCollectionKind(arrayName, collectionKind, CollectionKind.Array)
            else -> {
                // sequence.map returns a sequence.
                val elementMapper = CodeBlock.builder().also { block ->
                    val res = with(elementCodec) { block.codegenEncode(codegenContext, "it") }
                    block.addStatement(res)
                }
                elementMapper.build()
                "$local.map { ${elementMapper.build()} }".toCollectionKind(arrayName, CollectionKind.List,
                    CollectionKind.Array
                )
            }
        }
    }
}