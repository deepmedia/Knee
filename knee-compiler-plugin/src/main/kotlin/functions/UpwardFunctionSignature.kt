package io.deepmedia.tools.knee.plugin.compiler.functions

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.UNIT
import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codec.GenericCodec
import io.deepmedia.tools.knee.plugin.compiler.codec.IdentityCodec
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.context.KneeMapper
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.features.KneeUpwardFunction
import io.deepmedia.tools.knee.plugin.compiler.import.concrete
import io.deepmedia.tools.knee.plugin.compiler.jni.JniSignature
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.KneeSuspendInvocation
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrScriptSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly


class UpwardFunctionSignature(
    source: IrSimpleFunction,
    kind: KneeUpwardFunction.Kind,
    symbols: KneeSymbols,
    mapper: KneeMapper
) {

    // Jvm only.
    object Extra {
        val SuspendInvoker = DownwardFunctionSignature.Extra.SuspendInvoker
        val Receiver = DownwardFunctionSignature.Extra.ReceiverInstance
    }

    val isSuspend = source.isSuspend

    // Reverse suspend functions need to pass the return type from JVM to KN through a function
    // and there's no easy way to generate the exact signature. So we wrap it into java.lang.Object
    // This can be achieved by wrapping the codec with a GenericCodec
    val result: Codec = run {
        val codec = mapper.get(source.returnType.simple("UpwardSignature.result").concrete(kind.importInfo), source)
        when {
            !isSuspend -> codec
            else -> GenericCodec(symbols, codec)
        }
    }

    // Suspend function have a direct return type of KneeSuspendInvocation<Raw> on JVM, jobject on KN
    // This type should not be encoded or decoded so we wrap in a IdentityCodec
    // Note that Raw might be unit if the function returned void
    val suspendResult: Codec = IdentityCodec(JniType.Object(symbols, CodegenType.from(
        poetType = ClassName.bestGuess(KneeSuspendInvocation.asString()).parameterizedBy(
            typeArguments = arrayOf(result.encodedType.jvmOrNull?.name ?: UNIT)
        )
    )))

    // Note: this is the KneeInterface mapper, which technically encodes to Any and passes either a jobject or a long.
    // But reverse functions are only used for the K/N Impl classes, which point to a JVM implementation.
    // In this case the mapper passes the object as is, it's never a long.
    // val dispatchReceiver: Codec = mapper.get(localIrType = source.parentAsClass.thisReceiver!!.type)


    val extraParameters: List<Pair<Name, Codec>> = buildList {
        // Receiver: passing it as jobject, as is. => need identity codec
        // Suspend invoker: passing it as long
        add(Extra.Receiver to IdentityCodec(JniType.Object(
            symbols = symbols,
            jvm = CodegenType.from(source.parentAsClass.thisReceiver!!.type.simple("UpwardSignature.extraParams").concrete(kind.importInfo))
        )))
        if (isSuspend) {
            add(Extra.SuspendInvoker to mapper.get(symbols.builtIns.longType))
        }
    }

    val regularParameters: List<Pair<Name, Codec>> = source.valueParameters.map {
        it.name to mapper.get(it.type.simple("UpwardSignature.regularParams").concrete(kind.importInfo), it)
    }

    val jniInfo = JniInfo(source)

    inner class JniInfo internal constructor(
        private val source: IrSimpleFunction,
    ) {
        @Suppress("DefaultLocale")
        fun name(includeAncestors: Boolean): Name {
            val suffix = source.valueParameters.makeFunctionNameDisambiguationSuffix()

            fun mapper(name: String): String = "_\$" + when {
                source.isGetter -> "get${source.correspondingPropertySymbol!!.owner.name.asString().capitalizeAsciiOnly()}"
                source.isSetter -> "set${source.correspondingPropertySymbol!!.owner.name.asString().capitalizeAsciiOnly()}"
                else -> listOfNotNull(name, suffix).joinToString(separator = "_")
            }
            // Since this is used in codegen, it can't be a special name
            return when {
                includeAncestors -> source.codegenUniqueName(false, ::mapper)
                else -> source.codegenName.map(false, ::mapper)
            }
        }

        val signature: String = run {
            val returnType: JniType = (if (isSuspend) suspendResult else result).encodedType
            val prefixTypes: List<JniType> = extraParameters.map { (_, codec) -> codec.encodedType }
            val actualTypes: List<JniType> = regularParameters.map { (_, codec) -> codec.encodedType }
            JniSignature.get(
                returnType = returnType,
                argumentTypes = prefixTypes + actualTypes
            )
        }
    }
}

/**
 * Used when creating synthetic function names to disambiguate.
 * Needed because different types might use the same internal representation (e.g. two enums)
 * so there would be a clash between, say, getFoo(bar: Bar) and getFoo(baz: Baz) if Bar and Baz are enums
 *
 * Here we disambiguate by using the innermost type name, without looking at the package to avoid huge function names.
 * But that may be needed in the long run.
 * Note that old impl using IrType.hashCode and/or IrType.disambiguationHash was causing problems.
 */
internal fun List<IrValueParameter>.makeFunctionNameDisambiguationSuffix(): String? {
    if (this.isEmpty()) return null
    return map {
        val simpleType = it.type.simple("SignatureDisambiguation")
        val someName = when (val classifier = simpleType.classifier) {
            // relativeClassName is better in case of nested classes, e.g. Audio.Profile vs. Video.Profile
            is IrClassSymbol -> when (val classId = classifier.owner.classId) {
                null -> classifier.owner.name
                else -> {
                    val segments = classId.relativeClassName.pathSegments()
                    Name.identifier(segments.joinToString("") { it.asStringSafeForCodegen(false) })
                }
            }
            is IrTypeParameterSymbol -> classifier.owner.name
            is IrScriptSymbol -> classifier.owner.name
        }
        someName.asStringSafeForCodegen(false)
    }.joinToString(separator = "")
}