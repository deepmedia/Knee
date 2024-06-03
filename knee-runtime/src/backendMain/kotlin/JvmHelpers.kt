@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime


val currentJavaVirtualMachine: JavaVirtualMachine
    get() = checkNotNull(initializationData?.jvm) { "JVM is null. Did you forget to call initKnee?" }


inline fun <T> JavaVirtualMachine.useEnv(block: (JniEnvironment) -> T): T {
    var env = env
    if (env != null) {
        return block(env)
    }
    env = attachCurrentThread()
    return try {
        block(env)
    } finally {
        detachCurrentThread()
    }
}

// Just for compiler plugin, too lazy to use the property
// @PublishedApi internal fun JavaVirtualMachine.requireEnv(): JniEnvironment = env!!

// Just for compiler plugin, too lazy to use the property
// @PublishedApi internal fun JniEnvironment.requireJavaVM(): JavaVirtualMachine = javaVM

