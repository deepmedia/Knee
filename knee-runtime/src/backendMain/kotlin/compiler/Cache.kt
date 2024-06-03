package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import platform.android.jclass
import platform.android.jfieldID
import platform.android.jmethodID

private class Cache<Data> {
    private val map = mutableMapOf<String, Data>()
    private val lock = reentrantLock()
    inline fun get(key: String, defaultValue: () -> Data): Data {
        map[key]?.let { return it }
        return lock.withLock { map.getOrPut(key, defaultValue) }
    }
}

@PublishedApi
internal object MethodIds {
    private val cache = Cache<jmethodID>()

    // classFqn: java.lang.String
    fun get(
        env: JniEnvironment,
        classFqn: String,
        methodName: String,
        methodSignature: String,
        static: Boolean,
        classObject: jclass? = null,
    ): jmethodID {
        val key = "$classFqn::$methodName::$methodSignature::$static"
        return cache.get(key) {
            val jclass = classObject ?: ClassIds.get(env, classFqn)
            if (static) env.getStaticMethodId(jclass, methodName, methodSignature)
            else env.getMethodId(jclass, methodName, methodSignature)
        }
    }

    // NOTE: doesn't work for constructor of `inner` class
    // https://stackoverflow.com/a/25363953
    fun getConstructor(
        env: JniEnvironment,
        argsSignature: String,
        classFqn: String,
        classObject: jclass? = null,
    ): jmethodID = get(
        env = env,
        classFqn = classFqn,
        methodName = "<init>",
        methodSignature = "($argsSignature)V",
        static = false,
        classObject = classObject
    )
}

@PublishedApi
internal object ClassIds {
    private val cache = Cache<jclass>()

    /** for example. 'java.lang.String' */
    fun get(env: JniEnvironment, className: String): jclass {
        return cache.get(className) {
            // Safe deleteLocalRef: after find class it's needed to dispose the resource
            val klass = env.findClass(className)
            env.newGlobalRef(klass).also { env.deleteLocalRef(klass) }
        }
    }
}

@PublishedApi
internal object FieldIds {
    private val cache = Cache<jfieldID>()

    // classFqn: java.lang.String
    fun get(
        env: JniEnvironment,
        classFqn: String,
        fieldName: String,
        fieldSignature: String,
        static: Boolean,
        classObject: jclass? = null,
    ): jfieldID {
        val key = "$classFqn::$fieldName::$fieldSignature::$static"
        return cache.get(key) {
            val jclass = classObject ?: ClassIds.get(env, classFqn)
            if (static) env.getStaticFieldId(jclass, fieldName, fieldSignature)
            else env.getFieldId(jclass, fieldName, fieldSignature)
        }
    }
}