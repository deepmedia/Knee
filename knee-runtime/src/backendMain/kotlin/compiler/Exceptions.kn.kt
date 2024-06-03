package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.types.decodeClass
import io.deepmedia.tools.knee.runtime.types.decodeString
import io.deepmedia.tools.knee.runtime.types.encodeClass
import io.deepmedia.tools.knee.runtime.types.encodeString
import kotlinx.cinterop.*
import platform.android.jclass
import platform.android.jmethodID
import platform.android.jthrowable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.native.ref.createCleaner

private lateinit var exceptionToken: jclass
private lateinit var throwableGetMessage: jmethodID
private lateinit var classGetName: jmethodID
private lateinit var exceptionTokenCreate: jmethodID
private lateinit var exceptionTokenGet: jmethodID

internal fun initExceptions(environment: JniEnvironment) {
    environment.registerNatives(
        classFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeKnExceptionToken",
        bindings = arrayOf(JniNativeMethod(
            name = "clear",
            signature = "(J)V",
            pointer = staticCFunction<JniEnvironment, COpaquePointer, Long, Unit> { _, _, addr ->
                val ref = addr.toCPointer<CPointed>()?.asStableRef<Throwable>() ?: return@staticCFunction
                runCatching { ref.dispose() }
            }
        ))
    )
    throwableGetMessage = MethodIds.get(environment, "java.lang.Throwable", "getMessage", "()Ljava/lang/String;", false)
    classGetName = MethodIds.get(environment, "java.lang.Class", "getName", "()Ljava/lang/String;", false)
    exceptionToken = ClassIds.get(environment, "io.deepmedia.tools.knee.runtime.compiler.KneeKnExceptionToken")
    exceptionTokenCreate = MethodIds.get(environment, "io.deepmedia.tools.knee.runtime.compiler.KneeKnExceptionToken", "<init>", "(J)V", false, exceptionToken)
    exceptionTokenGet = MethodIds.get(environment, "io.deepmedia.tools.knee.runtime.compiler.KneeKnExceptionToken", "get", "(Ljava/lang/Throwable;)J", static = true, exceptionToken)
}

/**
 * Something failed in native code. We proceed as follows:
 *
 * - N1. If this was originally a JVM failure (see JVM2. in [processJvmException]), just rethrow
 *   the JVM exception as is so we are fully transparent, and code that relies on exception
 *   checks (e.g. coroutines collectWhile) will still work.
 *
 * - N2. Otherwise, this native failure must be transformed into a JVM exception.
 *   We want to inject the native failure into it as its [Throwable.cause], thus overriding
 *   the current cause. This is needed to implement the 2-way transparency behavior described above.
 *
 * When transforming into a JVM exception, we should do either of the following:
 *
 * - N2.1. Check if this throwable represents a @KneeClass type! In that case we can simply create
 *   an instance of the cloned type, after encoding this throwable as a long with encodeClass.
 *
 * - N2.2 Try to respect the exception type for common stdlib errors.
 *   We currently identify [CancellationException]s this way, and reuse the same [Throwable.message].
 *
 * - N2.3 Fallback to plain [RuntimeException], reusing at least the [Throwable.message].
 */
internal fun JniEnvironment.processNativeException(throwable: Throwable): jthrowable {
    run {
        val original = throwable.cause as? KneeJvmExceptionToken
        if (original != null) {
            // This K/N Throwable actually came from JVM. Since we stored the original
            // JVM exception, just use that instead of creating a new one. This makes exception
            // totally transparent which can be needed (example: flow.collectWhile).
            return original.reference
        }
    }

    run {
        val qualifiedName = throwable::class.qualifiedName
        val serializableExceptions = initializationData?.exceptions
        if (qualifiedName != null && serializableExceptions != null) {
            val match = serializableExceptions.firstOrNull { it.nativeFqn == qualifiedName }
            if (match != null) {
                // NOTE: constructor will be `internal` and part of another module, but we use `PublishedApi`
                // on the declaration and so it should be available here. About access, JNI doesn't check that.
                val jvmClass = ClassIds.get(this, match.jvmFqn)
                val jvmConstructor = MethodIds.getConstructor(this, "J", match.jvmFqn, jvmClass)
                val jvmInstance = newObject(jvmClass, jvmConstructor, encodeClass(throwable))
                return jvmInstance
            }
        }
    }

    // Fallback: create a new exception. Try respecting the class type and the message.
    // TODO: support more types
    val className = when (throwable) {
        is CancellationException -> "java.util.concurrent.CancellationException"
        else -> "java.lang.RuntimeException"
    }
    val message = throwable.message ?: "Unexpected native exception."
    val klass = ClassIds.get(this, className)
    val constructor = MethodIds.getConstructor(
        this,
        classFqn = className,
        argsSignature = "Ljava/lang/String;Ljava/lang/Throwable;",
        classObject = klass
    )
    val token = StableRef.create(throwable).asCPointer().toLong()
    val tokenHolder = newObject(exceptionToken, exceptionTokenCreate, token)
    return newObject(klass, constructor, encodeString(this, message), tokenHolder)
}


/**
 * Something failed in JVM code. We proceed as follows:
 *
 * - JVM1. If this was originally a native failure (see N2. in [processNativeException]), just rethrow
 *   the native exception as is so we are fully transparent, and code that relies on exception
 *   checks (e.g. coroutines collectWhile) will still work.
 *
 * - JVM2. Otherwise, this JVM failure must be transformed into a native exception.
 *   We want to inject the JVM failure into it as its [Throwable.cause], thus overriding
 *   the current cause. This is needed to implement the 2-way transparency behavior described above.
 *
 * When transforming into a native exception, we should do either of the following:
 *
 * - JVM2.1. Check if this throwable represents a @KneeClass type! In that case we can simply retrieve
 *   an instance of the original native type, after fetching the jthrowable's long and decoding with decodeClass.
 *
 * - JVM2.2. Try to respect the exception type for common stdlib errors.
 *   We currently identify [CancellationException]s this way, and reuse the same [Throwable.message].
 *
 * - jVM2.3. Fallback to plain [RuntimeException], reusing at least the [Throwable.message].
 */
internal fun JniEnvironment.processJvmException(throwable: jthrowable?): Throwable {
    if (throwable == null) return RuntimeException("Unexpected JVM exception.")

    // JVM1
    run {
        val original = callStaticLongMethod(exceptionToken, exceptionTokenGet, throwable)
        if (original != 0L) {
            // This JVM Throwable actually came from K/N. Since we stored the original
            // K/N exception, just use that instead of creating a new one. This makes exception
            // totally transparent which can be needed (example: flow.collectWhile).
            val res = original.toCPointer<CPointed>()?.asStableRef<Throwable>()?.get()
            if (res != null) return res
        }
    }

    val qualifiedName = decodeString(this, callObjectMethod(
        jobject = getObjectClass(throwable),
        method = classGetName
    )!!)

    // JVM2.1
    run {
        val serializableExceptions = initializationData?.exceptions
        if (serializableExceptions != null) {
            val match = serializableExceptions.firstOrNull { it.jvmFqnWithDots == qualifiedName }
            if (match != null) {
                val jvmHandleField = FieldIds.get(this, match.jvmFqn, "\$knee","J", false)
                val handle = getLongField(throwable, jvmHandleField)
                return decodeClass<Throwable>(handle)
            }
        }
    }

    // JVM2.2, JVM2.3
    val cause = KneeJvmExceptionToken(this, throwable)
    val message: String = run {
        val obj = callObjectMethod(throwable, throwableGetMessage) ?: return@run "Unexpected JVM exception."
        val chars = getStringUTFChars(obj)
        chars.toKStringFromUtf8().also { releaseStringUTFChars(obj, chars) }
    }
    // TODO: support more types
    return when (qualifiedName) {
        "java.util.concurrent.CancellationException" -> CancellationException(message, cause)
        else -> RuntimeException(message, cause)
    }
}

// Holds an exception that happened on the JVM side
private class KneeJvmExceptionToken(environment: JniEnvironment, throwable: jthrowable) : RuntimeException() {
    val reference = environment.newGlobalRef(throwable)
    private val jvm = environment.javaVM
    @Suppress("unused")
    private val referenceCleaner = createCleaner(this.jvm to this.reference) { (jvm, it) ->
        val env = jvm.env ?: jvm.attachCurrentThread()
        env.deleteGlobalRef(it)
    }
}

// Utilities used by the compiler

@PublishedApi
internal data class SerializableException(
    val nativeFqn: String, // com.package.MyClassName
    val jvmFqn: String // com/package/MyClassCodegenName
) {
    val jvmFqnWithDots by lazy { jvmFqn.replace("/", ".") }
}

@Suppress("unused")
@PublishedApi
internal fun JniEnvironment.rethrowNativeException(throwable: Throwable) {
    `throw`(processNativeException(throwable))
}
