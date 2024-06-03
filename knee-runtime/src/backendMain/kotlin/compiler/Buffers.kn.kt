package io.deepmedia.tools.knee.runtime.compiler

import io.deepmedia.tools.knee.runtime.*
import platform.android.*


private lateinit var bufferUtils: jclass
private lateinit var setByteBufferOrderMethod: jmethodID

internal fun initBuffers(environment: JniEnvironment) {
    bufferUtils = ClassIds.get(environment, "io.deepmedia.tools.knee.runtime.compiler.KneeBuffers")
    setByteBufferOrderMethod = MethodIds.get(
        env = environment,
        classFqn = "io.deepmedia.tools.knee.runtime.compiler.KneeBuffers",
        methodName = "setNativeOrder",
        methodSignature = "(Ljava/nio/ByteBuffer;)V",
        static = true,
        classObject = bufferUtils
    )
}

internal fun JniEnvironment.setDirectBufferNativeOrder(buffer: jobject) {
    callStaticVoidMethod(bufferUtils, setByteBufferOrderMethod, buffer)
}
