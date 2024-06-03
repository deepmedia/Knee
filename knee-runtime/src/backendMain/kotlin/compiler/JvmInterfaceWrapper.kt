package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.types.decodeString
import platform.android.*
import kotlin.native.ref.createCleaner

@Suppress("unused")
@PublishedApi
internal open class JvmInterfaceWrapper<T: Any>(
    environment: JniEnvironment,
    wrapped: jobject,
    interfaceFqn: String,
    private val methodOwnerFqn: String, // we write methods in the *Impl companion object
    vararg methodsAndSignatures: String // alternate method name and signature. Just to be easier to pass from IR
) {
    // accessed by IR
    val virtualMachine = environment.javaVM

    // needed to return the wrapped object to JVM
    // TODO: the deleteLocalRef below creates the warning: Attempt to remove non-JNI local reference
    //  When we, for example, pass a jobject to native and create an interface out of it. Concretely we are calling
    //  deleteLocalRef on the jobject from within the method, which is not needed.
    //  Same for almost all other usages of deleteLocalRef we do: it should be called by who creates
    //  We need to pass more information to codecs, to understand whether the resource should be released or not
    //  or maybe no resource needs to be released at all
    val jvmInterfaceObject = environment.newGlobalRef(wrapped).also {
        environment.deleteLocalRef(wrapped)
    }

    private val jvmInterfaceCleaner = createCleaner(this.virtualMachine to this.jvmInterfaceObject) { (virtualMachine, it) ->
        // looking at K/N code, looks like cleaner blocks are invoked on a special cleaner worker that should be long-lived,
        // in which case there's no harm to call attachCurrentThread() without detach. And since many
        // objects will be cleaned, calling attach+detach every time would easily hurt performance.
        // TODO: this still true? ^
        val env = virtualMachine.env ?: virtualMachine.attachCurrentThread()
        env.deleteGlobalRef(it)
    }

    private val equals: jmethodID
    private val hashCode: jmethodID
    private val toString: jmethodID
    init {
        val jclass = ClassIds.get(environment, interfaceFqn)
        equals = MethodIds.get(environment, interfaceFqn, "equals", "(Ljava/lang/Object;)Z", false, jclass)
        hashCode = MethodIds.get(environment, interfaceFqn, "hashCode", "()I", false, jclass)
        toString = MethodIds.get(environment, interfaceFqn, "toString", "()Ljava/lang/String;", false, jclass)
    }

    private val methodIds: Map<String, jmethodID> = run {
        val count = methodsAndSignatures.size / 2
        (0 until count).associate {
            val name = methodsAndSignatures[it * 2]
            val signature = methodsAndSignatures[it * 2 + 1]
            "$name::$signature" to MethodIds.get(environment, methodOwnerFqn, name, signature, static = true, methodOwnerClass)
        }
    }

    // needed to call methods using env.callStatic***Method()
    // accessed from IR
    val methodOwnerClass = ClassIds.get(environment, methodOwnerFqn)

    // key is name::signature
    fun method(key: String): jmethodID {
        return checkNotNull(methodIds[key]) { "Method $key not found. Available: ${methodIds.keys}" }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is JvmInterfaceWrapper<*>) return false
        return virtualMachine.useEnv { it.callBooleanMethod(jvmInterfaceObject, equals, other.jvmInterfaceObject) } == JNI_TRUE.toUByte()
    }

    override fun hashCode(): Int {
        return virtualMachine.useEnv { it.callIntMethod(jvmInterfaceObject, hashCode) }
    }

    override fun toString(): String {
        return virtualMachine.useEnv {
            val jstring = it.callObjectMethod(jvmInterfaceObject, toString)
            decodeString(it, jstring!!)
        }
    }
}