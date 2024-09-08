package io.deepmedia.tools.knee.plugin.compiler.symbols

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object KotlinIds {
    val UInt =                     ClassId(PackageNames.kotlin, Name.identifier("UInt"))
    val ULong =                    ClassId(PackageNames.kotlin, Name.identifier("ULong"))
    val UByte =                    ClassId(PackageNames.kotlin, Name.identifier("UByte"))
    val toUInt =                   CallableId(PackageNames.kotlin, Name.identifier("toUInt"))
    val toULong =                  CallableId(PackageNames.kotlin, Name.identifier("toULong"))
    val toUByte =                  CallableId(PackageNames.kotlin, Name.identifier("toUByte"))
    fun FunctionX(x: Int) =        ClassId(PackageNames.kotlin, Name.identifier("Function$x")) // this is in builtins too, but it crashes there
    fun SuspendFunctionX(x: Int) = ClassId(PackageNames.kotlinCoroutines, Name.identifier("SuspendFunction$x")) // this is in builtins too, but it crashes there
    val listOf =                   CallableId(PackageNames.kotlinCollections, Name.identifier("listOf"))
    val error =                    CallableId(PackageNames.kotlin, Name.identifier("error"))
    val Throwable =                ClassId(PackageNames.kotlin, Name.identifier("Throwable"))
}

// knee-annotations
object AnnotationIds {
    val KneeMetadata =  ClassId(PackageNames.annotations, Name.identifier("KneeMetadata"))
    val Knee =          FqName("io.deepmedia.tools.knee.annotations.Knee")
    val KneeEnum =      FqName("io.deepmedia.tools.knee.annotations.KneeEnum")
    val KneeClass =     FqName("io.deepmedia.tools.knee.annotations.KneeClass")
    val KneeObject =    FqName("io.deepmedia.tools.knee.annotations.KneeObject")
    val KneeInterface = FqName("io.deepmedia.tools.knee.annotations.KneeInterface")
    val KneeRaw =       FqName("io.deepmedia.tools.knee.annotations.KneeRaw")
}

object CInteropIds {
    val CPointer =        ClassId(PackageNames.cinterop, Name.identifier("CPointer"))
    val staticCFunction = CallableId(PackageNames.cinterop, Name.identifier("staticCFunction"))
    val COpaquePointer =  ClassId(PackageNames.cinterop, Name.identifier("COpaquePointer"))
}

object JDKIds {
    fun NioBuffer(type: String) = FqName("java.nio.${type}Buffer")
}

object PlatformIds {
    val jobject =      ClassId(PackageNames.platformAndroid, Name.identifier("jobject"))
    val jobjectArray = ClassId(PackageNames.platformAndroid, Name.identifier("jobjectArray"))
    val jclass =       ClassId(PackageNames.platformAndroid, Name.identifier("jclass"))
    val JNIEnvVar =    ClassId(PackageNames.platformAndroid, Name.identifier("JNIEnvVar"))
}

// knee-runtime
object RuntimeIds {
    val initKnee =                               CallableId(PackageNames.runtime, Name.identifier("initKnee"))
    val JNINativeMethod =                        ClassId(PackageNames.runtime, Name.identifier("JniNativeMethod"))
    val useEnv =                                 CallableId(PackageNames.runtime, Name.identifier("useEnv"))
    fun callStaticMethod(type: String) =         CallableId(PackageNames.runtime, Name.identifier("callStatic${type}Method"))

    val encodeClass =                            CallableId(PackageNames.runtimeTypes, Name.identifier("encodeClass"))
    val decodeClass =                            CallableId(PackageNames.runtimeTypes, Name.identifier("decodeClass"))
    val encodeEnum =                             CallableId(PackageNames.runtimeTypes, Name.identifier("encodeEnum"))
    val decodeEnum =                             CallableId(PackageNames.runtimeTypes, Name.identifier("decodeEnum"))
    val encodeString =                           CallableId(PackageNames.runtimeTypes, Name.identifier("encodeString"))
    val decodeString =                           CallableId(PackageNames.runtimeTypes, Name.identifier("decodeString"))
    val encodeBoolean =                          CallableId(PackageNames.runtimeTypes, Name.identifier("encodeBoolean"))
    val decodeBoolean =                          CallableId(PackageNames.runtimeTypes, Name.identifier("decodeBoolean"))
    val encodeInterface =                        CallableId(PackageNames.runtimeTypes, Name.identifier("encodeInterface"))
    val decodeInterface =                        CallableId(PackageNames.runtimeTypes, Name.identifier("decodeInterface"))
    fun encodeBoxed(type: String) =              CallableId(PackageNames.runtimeTypes, Name.identifier("encodeBoxed${type}"))
    fun decodeBoxed(type: String) =              CallableId(PackageNames.runtimeTypes, Name.identifier("decodeBoxed${type}"))

    val JObjectCollectionCodec =                 ClassId(PackageNames.runtimeCollections, Name.identifier("JObjectCollectionCodec"))
    val TransformingCollectionCodec =            ClassId(PackageNames.runtimeCollections, Name.identifier("TransformingCollectionCodec"))
    val typedArraySpec =                         CallableId(PackageNames.runtimeCollections, Name.identifier("typedArraySpec"))
    fun PrimitiveCollectionCodec(type: String) = ClassId(PackageNames.runtimeCollections, Name.identifier("${type}CollectionCodec"))
    fun PrimitiveArraySpec(type: String) =       ClassId(PackageNames.runtimeCollections, FqName("ArraySpec.${type}s"), false)

    val KneeModule =                             ClassId(PackageNames.runtimeModule, Name.identifier("KneeModule"))
    val KneeModule_getExportAdapter =            CallableId(KneeModule, Name.identifier("getExportAdapter"))
    private val KneeModuleBuilder =              ClassId(PackageNames.runtimeModule, Name.identifier("KneeModuleBuilder"))
    val KneeModuleBuilder_export =               CallableId(KneeModuleBuilder, Name.identifier("export"))
    val KneeModuleBuilder_exportAdapter =        CallableId(KneeModuleBuilder, Name.identifier("exportAdapter"))

    val Adapter =                                KneeModule.createNestedClassId(Name.identifier("Adapter"))
    val Adapter_decode =                         CallableId(Adapter, Name.identifier("decode"))
    val Adapter_encode =                         CallableId(Adapter, Name.identifier("encode"))

    fun PrimitiveBuffer(type: String) =          ClassId(PackageNames.runtimeBuffer, Name.identifier("${type}Buffer"))

    val KneeSuspendInvoker =                     FqName("io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvoker")
    val KneeSuspendInvocation =                  FqName("io.deepmedia.tools.knee.runtime.compiler.KneeSuspendInvocation")
    val kneeInvokeJvmSuspend =                   CallableId(PackageNames.runtimeCompiler, Name.identifier("kneeInvokeJvmSuspend"))
    val kneeInvokeKnSuspend =                    CallableId(PackageNames.runtimeCompiler, Name.identifier("kneeInvokeKnSuspend"))
    val JvmInterfaceWrapper =                    ClassId(PackageNames.runtimeCompiler, Name.identifier("JvmInterfaceWrapper"))
    val rethrowNativeException =                 CallableId(PackageNames.runtimeCompiler, Name.identifier("rethrowNativeException"))
    val SerializableException =                  ClassId(PackageNames.runtimeCompiler, Name.identifier("SerializableException"))
}