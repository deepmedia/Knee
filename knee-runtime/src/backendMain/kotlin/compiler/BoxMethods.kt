@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import platform.android.*


internal fun initBoxMethods(environment: JniEnvironment) = with(BoxMethods) {
    longConstructor = MethodIds.get(environment, "java.lang.Long", "<init>", "(J)V", false)
    longValue = MethodIds.get(environment, "java.lang.Long", "longValue", "()J", false)
    intConstructor = MethodIds.get(environment, "java.lang.Integer", "<init>", "(I)V", false)
    intValue = MethodIds.get(environment, "java.lang.Integer", "intValue", "()I", false)
    byteConstructor = MethodIds.get(environment, "java.lang.Byte", "<init>", "(B)V", false)
    byteValue = MethodIds.get(environment, "java.lang.Byte", "byteValue", "()B", false)
    boolConstructor = MethodIds.get(environment, "java.lang.Boolean", "<init>", "(Z)V", false)
    boolValue = MethodIds.get(environment, "java.lang.Boolean", "booleanValue", "()Z", false)
    doubleConstructor = MethodIds.get(environment, "java.lang.Double", "<init>", "(D)V", false)
    doubleValue = MethodIds.get(environment, "java.lang.Double", "doubleValue", "()D", false)
    floatConstructor = MethodIds.get(environment, "java.lang.Float", "<init>", "(F)V", false)
    floatValue = MethodIds.get(environment, "java.lang.Float", "floatValue", "()F", false)
}

@PublishedApi
internal object BoxMethods {
    lateinit var longConstructor: jmethodID
    lateinit var longValue: jmethodID
    lateinit var intConstructor: jmethodID
    lateinit var intValue: jmethodID
    lateinit var byteConstructor: jmethodID
    lateinit var byteValue: jmethodID
    lateinit var boolConstructor: jmethodID
    lateinit var boolValue: jmethodID
    lateinit var doubleConstructor: jmethodID
    lateinit var doubleValue: jmethodID
    lateinit var floatConstructor: jmethodID
    lateinit var floatValue: jmethodID
}
