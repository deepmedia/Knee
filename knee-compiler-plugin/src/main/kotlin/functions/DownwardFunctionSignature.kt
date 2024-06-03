package io.deepmedia.tools.knee.plugin.compiler.functions

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import io.deepmedia.tools.knee.plugin.compiler.codec.*
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.codegen.KneeCodegen
import io.deepmedia.tools.knee.plugin.compiler.context.KneeContext
import io.deepmedia.tools.knee.plugin.compiler.features.KneeDownwardFunction.Kind
import io.deepmedia.tools.knee.plugin.compiler.features.KneeDownwardFunction
import io.deepmedia.tools.knee.plugin.compiler.import.concrete
import io.deepmedia.tools.knee.plugin.compiler.jni.JniSignature
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import io.deepmedia.tools.knee.plugin.compiler.utils.*
import io.deepmedia.tools.knee.plugin.compiler.symbols.CInteropIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.PlatformIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds.KneeSuspendInvoker
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name


/**
 * Represents the signature of a user defined K/N function which is supposed to be called from JVM.
 * - [Kind.TopLevel]: a top level KN function
 * - [Kind.ClassConstructor]: constructor of some KN class
 * - [Kind.ClassMember]: member of some KN class
 * - [Kind.InterfaceMember]: member of some KN interface
 *
 * The function bridging mechanism is pretty complex - look at [KneeDownwardFunction] for source code info.
 * The gist is that we have to execute code before JNI is invoked and after the JNI result is received,
 * on both ends of the function (KN - JVM).
 * - JVM: encodes the parameters in a JNI friendly [JniType]
 * - JVM: invokes the JNI function
 * - KN: receives the JNI function call
 * - KN: decodes the JNI parameters in some KN-local [IrType]
 * - KN: executes the original user-defined function
 * - KN: encodes the result in a JNI friendly [JniType]
 * - JVM: receives the result and decodes it in some JVM-local [CodegenType]
 * The existence of all these steps means that some wrapper functions must be generated where we can
 * executed the encode / decode code, as defined by the [Codec] associated with the type that must cross JNI.
 *
 * So we have to generate:
 * 1. Some 'external' function on the JVM side. This function might have extra parameters as per [extraParameters]
 * 2. Some wrapper function on the KN side. This function might have extra parameters as per [extraParameters]
 *   and also has jni prefix parameters as per [knPrefixParameters]
 * Note that extras are added by us to ease the function execution, like in class members or suspend functions.
 *
 * In addition, things get more complex because of the @KneeRaw annotation, here represented by
 * the [DownwardFunctionSignature.RawKind] interface:
 * - If it specifies a number, that number is the index of the [knPrefixParameters] parameter which should be
 *   copied into that parameter. Can be used for example to access the environment or the jobject / jclass.
 *   Such copying parameters are exposed in [knCopyParameters].
 * - If it specifies a fully qualified name, that's the name of a JVM-specific class which doesn't exist
 *   in K/N, that we want to map with that specific raw parameter. For example, we might want to get
 *   a Surface from Android as a raw jobject which can be later passed to the Android NDK APIs.
 *   Such parameters are exposed in [regularParameters] together will all other params.
 *
 * This class tries to expose the [Codec]s where possible, so that consumers can choose which type to use
 * and where / when.
 */
class DownwardFunctionSignature(source: IrFunction, kind: Kind, context: KneeContext) {

    object Extra {
        // kotlinpoet has a rule for which args must start with lowercase letter
        val SuspendInvoker = Name.identifier("suspendInvoker__")
        val ReceiverInstance = Name.identifier("instance__")
    }

    object KnPrefix {
        val JniEnvironment = Name.identifier("__jniEnvironment")
        val JniObjectOrClass = Name.identifier("__jniObjectOrClass")
    }

    val isSuspend = source.isSuspend

    val result: Codec = run {
        val type = source.returnType.simple("DownwardSignature.result").concrete(kind.importInfo)
        val codec = context.mapper.get(type, source)
        when {
            !isSuspend -> codec
            else -> GenericCodec(context.symbols, codec)
        }
    }

    val suspendResult: Codec = context.mapper.get(context.symbols.builtIns.longType)

    val extraParameters: List<Pair<Name, Codec>> = buildList {
        // instance member functions should pass the handle reference so that we can decode them in IR.
        if (kind is Kind.ClassMember || kind is Kind.InterfaceMember) {
            add(Extra.ReceiverInstance to context.mapper.get(source.parentAsClass.thisReceiver!!.type.simple("DownwardSignature.extraParams").concrete(kind.importInfo)))
        }
        // suspend functions should pass the 'continuation'. It is an instance of KneeSuspendInvoker<RawResultType>
        // note that encoded type might very well be Unit if the function returned void
        if (isSuspend) {
            add(Extra.SuspendInvoker to IdentityCodec(JniType.Object(
                symbols = context.symbols,
                jvm = CodegenType.from(ClassName
                    .bestGuess(KneeSuspendInvoker.asString())
                    .parameterizedBy(result.encodedType.jvmOrNull?.name ?: UNIT))
            )))
        }
    }

    val regularParameters: List<Pair<Name, Codec>> = source.valueParameters.map {
        it.name to context.mapper.get(it.type.simple("DownwardSignature.regularParams").concrete(kind.importInfo), it)
        /* it.name to when (val rawKind = it.rawKind) {
            null -> mapper.get(it.type, kind.importInfo)
            // is RawKind.CopyAtIndex -> null
            is RawKind.Class -> {
                // TODO: it should be possible to include generics in fqName, we should parse them
                val jobject = JniType.Object(context.symbols, CodegenType.from(rawKind.fqName))
                require(jobject.kn.makeNullable() == it.type.makeNullable()) {
                    "@KneeRaw(${rawKind.fqName}) should be applied on a parameter of type 'jobject' or similar."
                }
                IdentityCodec(type = jobject)
            }
        } */
    }

    /**
     * The IR bridge function has extra parameters that we call "prefix" parameters. These are:
     * - a pointer to the JNI environment, JNIEnv*
     * - a jobject or jclass depending on the type of function (member vs static)
     */
    val knPrefixParameters: List<Pair<Name, IrType>> = buildList {
        // pointer to jni env
        add(KnPrefix.JniEnvironment to context.symbols.klass(CInteropIds.CPointer)
            .typeWith(context.symbols.typeAliasUnwrapped(PlatformIds.JNIEnvVar)))
        // jobject or jclass depending on static vs instance function. In practice this won't make
        // any difference because jclass is a typealias for jobject, but whatever.
        add(KnPrefix.JniObjectOrClass to context.symbols.typeAliasUnwrapped(when (kind) {
            is Kind.TopLevel,
            is Kind.ClassConstructor -> PlatformIds.jobject
            is Kind.ClassMember -> PlatformIds.jclass
            is Kind.InterfaceMember -> PlatformIds.jclass
        }))
    }

    /**
     * Unsubstituted meaning that we don't pass importInfo, so generics are preserved as raw types.
     * One could just use type.asTypeName() but we must pass through the mapper for some edge scenarios,
     * like @KneeRaw-annotated declarations or other things.
     */
    val unsubstitutedValueParametersForCodegen: List<Pair<Name, TypeName>> = source.valueParameters.map {
        it.name to (runCatching { context.mapper.get(it.type, it) }.getOrNull()?.localCodegenType?.name ?: it.type.simple("DownwardSignature.valueParams").asTypeName())
    }

    /**
     * Unsubstituted meaning that we don't pass importInfo, so generics are preserved as raw types.
     * One could just use type.asTypeName() but we must pass through the mapper for some edge scenarios,
     * like @KneeRaw-annotated declarations or other things.
     */
    val unsubstitutedReturnTypeForCodegen: TypeName = run {
        runCatching { context.mapper.get(source.returnType, source) }.getOrNull()?.localCodegenType?.name ?: source.returnType.simple("DownwardSignature.valueParams").asTypeName()
    }

    /**
     * Note: [Int] here is the index to be copied in the whole array, including prefixes and extras.
     * The [Name] can be used to identify the position of this parameter in the user defined function.
     */
    /* val knCopyParameters: List<Pair<Name, Int>> = source.valueParameters.mapNotNull {
        when (val rawKind = it.rawKind) {
            is RawKind.CopyAtIndex -> it.name to rawKind.index
            is RawKind.Class -> null
            else -> null
        }
    } */

    /* @Suppress("UNCHECKED_CAST")
    private val IrValueParameter.rawKind: RawKind? get() {
        val annotation = getAnnotation(Names.kneeRawAnnotation) ?: return null
        val content = (annotation.getValueArgument(0)!! as IrConst<String>).value
        return RawKind.Class(content)
        /* return when (val int = content.toIntOrNull()) {
            null -> RawKind.Class(content)
            else -> RawKind.CopyAtIndex(int)
        } */
    } */

    /**
     * Arguments of @Knee annotated functions can use @KneeRaw to say that
     * they want to receive one of the copy parameters or a raw class.
     */
    /* private sealed class RawKind {
        // data class CopyAtIndex(val index: Int) : RawKind()
        data class Class(val fqName: String) : RawKind()
    } */

    val jniInfo = JniInfo(source, kind, context.module)

    // Used for registerNatives / codegen
    inner class JniInfo internal constructor(
        private val source: IrFunction,
        private val kind: Kind,
        module: IrModuleFragment
    ) {

        val owner: CodegenType by lazy {
            when (kind) {
                is Kind.InterfaceMember -> kind.owner.codegenImplementation.type
                is Kind.ClassMember -> kind.owner.codegenClone.type
                is Kind.ClassConstructor -> {
                    // Technically for constructors we codegen in the companion object, but it makes no difference
                    // from the JVM perspective, it's a function inside the owner class.
                    kind.owner.codegenClone.type
                }
                is Kind.TopLevel -> {
                    val packageName = (kind.importInfo?.file ?: source.file).packageFqName
                    val className = "${KneeCodegen.Filename}Kt"
                    CodegenType.from("$packageName.$className")
                }
            }
        }

        @Suppress("DefaultLocale")
        fun name(includeAncestors: Boolean): Name {

            // when ancestors are required for higher disambiguation, we must include the importInfo id.
            val suffix = source.valueParameters.makeFunctionNameDisambiguationSuffix()
            val prefix = kind.importInfo?.id?.takeIf { includeAncestors }

            fun mapper(name: String): String = "$" + when (kind) {
                is Kind.TopLevel,
                is Kind.ClassMember,
                is Kind.InterfaceMember -> listOfNotNull(prefix, name, suffix).joinToString(separator = "_")
                is Kind.ClassConstructor -> {
                    // standard name for constructors is <init> for all of them, so we must make it unique in some way.
                    val index = source.parentAsClass.constructors.toList().indexOf(source as IrConstructor)
                    listOfNotNull(prefix, "${name}${index}").joinToString(separator = "_")
                }
            }
            // Since this is used in codegen, it can't be a special name
            return when {
                includeAncestors -> source.codegenUniqueName(false, ::mapper)
                else -> source.codegenName.map(false, ::mapper)
            }
        }

        val signature: String by lazy {
            val returnType: JniType = (if (isSuspend) suspendResult else result).encodedType
            val extraTypes: List<JniType> = extraParameters.map { (_, codec) -> codec.encodedType }
            val actualTypes: List<JniType> = regularParameters.map { (_, codec) -> codec.encodedType }
            JniSignature.get(
                returnType = returnType,
                argumentTypes = extraTypes + actualTypes
            )
        }
    }

}