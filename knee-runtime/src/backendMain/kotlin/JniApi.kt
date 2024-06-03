@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime

import io.deepmedia.tools.knee.runtime.compiler.processJvmException
import kotlinx.cinterop.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.Int
import platform.android.*

// JNI APIs, with same namings and so on, just wrapped in a more convenient
// fashion, as extensions to CPointer<*>.

typealias JniEnvironment = CPointer<JNIEnvVar>

@PublishedApi
internal val JniEnvironment.api get() = pointed.pointed!!

val JniEnvironment.javaVM: JavaVirtualMachine get() = memScoped {
    val jvmPointer: CPointerVar<JavaVMVar> = allocPointerTo()
    val res = api.GetJavaVM!!(this@javaVM, jvmPointer.ptr)
    check(res == JNI_OK) { "GetJavaVM failed: $res" }
    jvmPointer.pointed!!.ptr
}

fun JniEnvironment.findClass(name: String): jclass = memScoped {
    // println("findClass=$name")
    // try {
        val jclass = api.FindClass!!(this@findClass, name.replace('.', '/').cstr.ptr)
        checkNotNull(jclass) {
            checkPendingJvmException()
            "FindClass failed: class $name not found."
        }
    /* } catch (e: Throwable) {
        println("findClass=$name FAILED=$e")
        throw e
    } finally {
        println("findClass=$name END")
    } */
}

data class JniNativeMethod(
    val name: String,
    val signature: String,
    val pointer: COpaquePointer
)

fun JniEnvironment.registerNatives(
    classFqn: String,
    vararg bindings: JniNativeMethod
): Unit = registerNatives(jclass = findClass(classFqn), bindings = bindings)

fun JniEnvironment.registerNatives(
    jclass: jclass,
    vararg bindings: JniNativeMethod
): Unit = memScoped {
    val jniMethods = allocArray<JNINativeMethod>(bindings.size)
    bindings.forEachIndexed { index, binding ->
        jniMethods[index].fnPtr = binding.pointer
        jniMethods[index].name = binding.name.cstr.ptr
        jniMethods[index].signature = binding.signature.cstr.ptr
    }
    val result = api.RegisterNatives!!(this@registerNatives, jclass, jniMethods, bindings.size)
    check(result == 0) { "RegisterNatives failed: $result" }
}

fun JniEnvironment.getStringUTFChars(
    jstring: jstring
): CPointer<ByteVar> = memScoped {
    val result = api.GetStringUTFChars!!(this@getStringUTFChars, jstring, null)
    checkNotNull(result) { "GetStringUTFChars failed." }
    result
}

fun JniEnvironment.releaseStringUTFChars(
    jstring: jstring,
    chars: CPointer<ByteVar>
): Unit = memScoped {
    api.ReleaseStringUTFChars!!(this@releaseStringUTFChars, jstring, chars)
}

fun JniEnvironment.newStringUTF(string: String) = memScoped {
    newStringUTF(utfChars = string.utf8.ptr)
}

fun JniEnvironment.newStringUTF(
    utfChars: CPointer<ByteVar>, // null terminated
): jstring = memScoped {
    val res = api.NewStringUTF!!(this@newStringUTF, utfChars)
    checkNotNull(res) { "newStringUTF failed." }
    return res
}

fun JniEnvironment.getArrayLength(array: jarray): Int {
    return api.GetArrayLength!!(this, array)
}

// Ignoring copy flag...
fun JniEnvironment.getPrimitiveArrayCritical(array: jarray): COpaquePointer {
    return checkNotNull(api.GetPrimitiveArrayCritical!!(this, array, null)) {
        "GetPrimitiveArrayCritical failed."
    }
}

fun JniEnvironment.releasePrimitiveArrayCritical(array: jarray, handle: COpaquePointer, mode: kotlin.Int) {
    api.ReleasePrimitiveArrayCritical!!(this, array, handle, mode)
}

@OptIn(ExperimentalContracts::class)
inline fun <R> JniEnvironment.usePrimitiveArrayCritical(array: jarray, mode: kotlin.Int, block: (COpaquePointer) -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val handle = getPrimitiveArrayCritical(array)
    return try { block(handle) } finally {
        releasePrimitiveArrayCritical(array, handle, mode)
    }
}

fun JniEnvironment.getObjectArrayElement(array: jobjectArray, index: Int): jobject {
    return checkNotNull(api.GetObjectArrayElement!!(this, array, index)) { "GetObjectArrayElement failed." }
}

fun JniEnvironment.setObjectArrayElement(array: jobjectArray, index: Int, value: jobject) {
    api.SetObjectArrayElement!!(this, array, index, value)
}

fun JniEnvironment.newByteArray(size: Int): jbyteArray {
    return checkNotNull(api.NewByteArray!!(this, size)) { "NewByteArray failed." }
}

fun JniEnvironment.newIntArray(size: Int): jintArray {
    return checkNotNull(api.NewIntArray!!(this, size)) { "NewIntArray failed." }
}

fun JniEnvironment.newLongArray(size: Int): jlongArray {
    return checkNotNull(api.NewLongArray!!(this, size)) { "NewLongArray failed." }
}

fun JniEnvironment.newFloatArray(size: Int): jfloatArray {
    return checkNotNull(api.NewFloatArray!!(this, size)) { "NewFloatArray failed." }
}

fun JniEnvironment.newDoubleArray(size: Int): jdoubleArray {
    return checkNotNull(api.NewDoubleArray!!(this, size)) { "NewDoubleArray failed." }
}

fun JniEnvironment.newBooleanArray(size: Int): jbooleanArray {
    return checkNotNull(api.NewBooleanArray!!(this, size)) { "NewBooleanArray failed." }
}

fun JniEnvironment.newObjectArray(size: Int, klass: jclass, initial: jobject? = null): jobjectArray {
    return checkNotNull(api.NewObjectArray!!(this, size, klass, initial)) { "NewObjectArray failed." }
}

@Suppress("UNCHECKED_CAST")
fun JniEnvironment.newObject(klass: jclass, constructor: jmethodID, vararg args: Any?): jobject {
    if (args.isEmpty()) {
        val func = api.NewObject!! as CPointer<CFunction<(JniEnvironment, jclass, jmethodID) -> jobject?>>
        func(this, klass, constructor)
    } else {
        memScoped {
            val array = allocArray<jvalue>(args.size)
            args.forEachIndexed { index, arg -> arg.jvalueOrThrow(array[index]) }
            api.NewObjectA!!(this@newObject, klass, constructor, array)
        }
    }.let {
        return checkNotNull(it) { "NewObject failed." }
    }
}

fun JniEnvironment.newDirectByteBuffer(address: COpaquePointer, capacity: Long): jobject {
    return checkNotNull(api.NewDirectByteBuffer!!(this, address, capacity)) { "newDirectByteBuffer failed!" }
}

fun JniEnvironment.getDirectBufferAddress(buffer: jobject): COpaquePointer {
    return checkNotNull(api.GetDirectBufferAddress!!(this, buffer)) { "GetDirectBufferAddress failed!" }
}

fun JniEnvironment.getDirectBufferCapacity(buffer: jobject): Long {
    return checkNotNull(api.GetDirectBufferCapacity!!(this, buffer)) { "GetDirectBufferCapacity failed!" }
}

fun JniEnvironment.setByteArrayRegion(array: jbyteArray, start: Int, length: Int, data: CPointer<jbyteVar>) {
    api.SetByteArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.setIntArrayRegion(array: jintArray, start: Int, length: Int, data: CPointer<jintVar>) {
    api.SetIntArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.setLongArrayRegion(array: jlongArray, start: Int, length: Int, data: CPointer<jlongVar>) {
    api.SetLongArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.setFloatArrayRegion(array: jfloatArray, start: Int, length: Int, data: CPointer<jfloatVar>) {
    api.SetFloatArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.setDoubleArrayRegion(array: jdoubleArray, start: Int, length: Int, data: CPointer<jdoubleVar>) {
    api.SetDoubleArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.setBooleanArrayRegion(array: jbooleanArray, start: Int, length: Int, data: CPointer<jbooleanVar>) {
    api.SetBooleanArrayRegion!!(this, array, start, length, data)
}

fun JniEnvironment.newGlobalRef(jobject: jobject): jobject {
    return checkNotNull(api.NewGlobalRef!!(this, jobject)) {
        "NewGlobalRef failed, out of memory?"
    }
}

fun JniEnvironment.deleteLocalRef(jobject: jobject) {
    api.DeleteLocalRef!!(this, jobject)
}

fun JniEnvironment.deleteGlobalRef(jobject: jobject) {
    api.DeleteGlobalRef!!(this, jobject)
}

fun JniEnvironment.isSameObject(first: jobject, second: jobject): Boolean {
    return api.IsSameObject!!(this, first, second).toInt() == JNI_TRUE
}

fun JniEnvironment.getObjectClass(jobject: jobject): jclass {
    return checkNotNull(api.GetObjectClass!!(this, jobject)) {
        "getObjectClass failed"
    }
}

fun JniEnvironment.getMethodId(jclass: jclass, name: String, signature: String): jmethodID = memScoped {
    checkNotNull(api.GetMethodID!!(this@getMethodId, jclass, name.cstr.ptr, signature.cstr.ptr)) {
        // Checking for null is enough to understand if this failed, but there might still be an exception at the JVM level
        // like NoSuchMethodError. Throw that if present, so future JNI calls will not fail because it will be cleared.
        checkPendingJvmException()
        "getMethodId failed ($jclass, $name, $signature)"
    }
}

fun JniEnvironment.getStaticMethodId(jclass: jclass, name: String, signature: String): jmethodID = memScoped {
    checkNotNull(api.GetStaticMethodID!!(this@getStaticMethodId, jclass, name.cstr.ptr, signature.cstr.ptr)) {
        // Checking for null is enough to understand if this failed, but there might still be an exception at the JVM level
        // like NoSuchMethodError. Throw that if present, so future JNI calls will not fail because it will be cleared.
        checkPendingJvmException()
        "getStaticMethodId failed ($jclass, $name, $signature)"
    }
}

fun JniEnvironment.getFieldId(jclass: jclass, name: String, signature: String): jfieldID = memScoped {
    checkNotNull(api.GetFieldID!!(this@getFieldId, jclass, name.cstr.ptr, signature.cstr.ptr)) {
        // Checking for null is enough to understand if this failed, but there might still be an exception at the JVM level
        // like NoSuchMethodError. Throw that if present, so future JNI calls will not fail because it will be cleared.
        checkPendingJvmException()
        "getMethodId failed ($jclass, $name, $signature)"
    }
}

fun JniEnvironment.getStaticFieldId(jclass: jclass, name: String, signature: String): jfieldID = memScoped {
    checkNotNull(api.GetStaticFieldID!!(this@getStaticFieldId, jclass, name.cstr.ptr, signature.cstr.ptr)) {
        // Checking for null is enough to understand if this failed, but there might still be an exception at the JVM level
        // like NoSuchMethodError. Throw that if present, so future JNI calls will not fail because it will be cleared.
        checkPendingJvmException()
        "getMethodId failed ($jclass, $name, $signature)"
    }
}

@PublishedApi
internal fun Any?.jvalueOrThrow(value: jvalue) {
    when (this) {
        null -> value.l = null
        is CPointer<*> -> value.l = this
        is jshort -> value.s = this
        is jchar -> value.c = this
        is jdouble -> value.d = this
        is jbyte -> value.b = this
        is jfloat -> value.f = this
        is jint -> value.i = this
        is jlong -> value.j = this
        is jboolean -> value.z = this
        else -> error("Unsupported argument: $this")
    }
}

/**
 * Note: [noArgsInvoke] and [arrayArgsInvoke] are a workaround for https://youtrack.jetbrains.com/issue/KT-55776
 * The function invocation must happen on the parent function otherwise compiler throws the error above.
 */
@PublishedApi
internal inline fun <reified ReturnType> JniEnvironment.callMethod(
    jobjectOrJClass: COpaquePointer,
    method: jmethodID,
    noArgsFun: JNINativeInterface.() -> COpaquePointer,
    arrayArgsFun: JNINativeInterface.() -> CPointer<CFunction<(JniEnvironment?, COpaquePointer?, jmethodID?, CPointer<jvalue>?) -> ReturnType>>,
    noArgsInvoke: CPointer<CFunction<(JniEnvironment?, COpaquePointer?, jmethodID?) -> ReturnType>>.(JniEnvironment, COpaquePointer, jmethodID) -> ReturnType,
    arrayArgsInvoke: CPointer<CFunction<(JniEnvironment?, COpaquePointer?, jmethodID?, CPointer<jvalue>?) -> ReturnType>>.(JniEnvironment, COpaquePointer, jmethodID, CPointer<jvalue>?) -> ReturnType,
    vararg args: Any?,
): ReturnType {
    return if (args.isEmpty()) {
        @Suppress("UNCHECKED_CAST")
        val func = api.noArgsFun() as CPointer<CFunction<(JniEnvironment?, COpaquePointer?, jmethodID?) -> ReturnType>>
        func.noArgsInvoke(this, jobjectOrJClass, method)
    } else {
        memScoped {
            val array = allocArray<jvalue>(args.size)
            args.forEachIndexed { index, arg -> arg.jvalueOrThrow(array[index]) }
            api.arrayArgsFun().arrayArgsInvoke(this@callMethod, jobjectOrJClass, method, array)
        }
    }.also {
        checkPendingJvmException()
    }
}

fun JniEnvironment.callObjectMethod(jobject: jobject, method: jmethodID, vararg args: Any?): jobject? {
    return callMethod<jobject?>(jobject, method, { CallObjectMethod!! }, { CallObjectMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callBooleanMethod(jobject: jobject, method: jmethodID, vararg args: Any?): jboolean {
    return callMethod<jboolean>(jobject, method, { CallBooleanMethod!! }, { CallBooleanMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callByteMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Byte {
    return callMethod<jbyte>(jobject, method, { CallByteMethod!! }, { CallByteMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callCharMethod(jobject: jobject, method: jmethodID, vararg args: Any?): jchar {
    return callMethod<jchar>(jobject, method, { CallCharMethod!! }, { CallCharMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callShortMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Short {
    return callMethod<jshort>(jobject, method, { CallShortMethod!! }, { CallShortMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callIntMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Int {
    return callMethod<jint>(jobject, method, { CallIntMethod!! }, { CallIntMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callLongMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Long {
    return callMethod<jlong>(jobject, method, { CallLongMethod!! }, { CallLongMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callFloatMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Float {
    return callMethod<jfloat>(jobject, method, { CallFloatMethod!! }, { CallFloatMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callDoubleMethod(jobject: jobject, method: jmethodID, vararg args: Any?): Double {
    return callMethod<jdouble>(jobject, method, { CallDoubleMethod!! }, { CallDoubleMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callVoidMethod(jobject: jobject, method: jmethodID, vararg args: Any?) {
    return callMethod<Unit>(jobject, method, { CallVoidMethod!! }, { CallVoidMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticObjectMethod(jclass: jclass, method: jmethodID, vararg args: Any?): jobject? {
    return callMethod<jobject?>(jclass, method, { CallStaticObjectMethod!! }, { CallStaticObjectMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticBooleanMethod(jclass: jclass, method: jmethodID, vararg args: Any?): jboolean {
    return callMethod<jboolean>(jclass, method, { CallStaticBooleanMethod!! }, { CallStaticBooleanMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticByteMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Byte {
    return callMethod<jbyte>(jclass, method, { CallStaticByteMethod!! }, { CallStaticByteMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticCharMethod(jclass: jclass, method: jmethodID, vararg args: Any?): jchar {
    return callMethod<jchar>(jclass, method, { CallStaticCharMethod!! }, { CallStaticCharMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticShortMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Short {
    return callMethod<jshort>(jclass, method, { CallStaticShortMethod!! }, { CallStaticShortMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticIntMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Int {
    return callMethod<jint>(jclass, method, { CallStaticIntMethod!! }, { CallStaticIntMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticLongMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Long {
    return callMethod<jlong>(jclass, method, { CallStaticLongMethod!! }, { CallStaticLongMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticFloatMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Float {
    return callMethod<jfloat>(jclass, method, { CallStaticFloatMethod!! }, { CallStaticFloatMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticDoubleMethod(jclass: jclass, method: jmethodID, vararg args: Any?): Double {
    return callMethod<jdouble>(jclass, method, { CallStaticDoubleMethod!! }, { CallStaticDoubleMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.callStaticVoidMethod(jclass: jclass, method: jmethodID, vararg args: Any?) {
    return callMethod<Unit>(jclass, method, { CallStaticVoidMethod!! }, { CallStaticVoidMethodA!! }, { a,b,c->this(a,b,c) }, { a,b,c,d->this(a,b,c,d) }, *args)
}

fun JniEnvironment.`throw`(throwable: jthrowable) {
    val res = api.Throw!!(this, throwable)
    check(res == 0) { "Throw failed: $res" }
}

@PublishedApi // < because it's used in inline fun
internal fun JniEnvironment.checkPendingJvmException() {
    if (api.ExceptionCheck!!(this) == platform.android.JNI_TRUE.toUByte()) {
        val throwable = api.ExceptionOccurred!!(this)
        api.ExceptionClear!!(this)
        throw processJvmException(throwable)
    }
}