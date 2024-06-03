package io.deepmedia.tools.knee.runtime.types

import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.compiler.ClassIds
import io.deepmedia.tools.knee.runtime.compiler.BoxMethods
import platform.android.*

@PublishedApi
internal fun encodeBoxedLong(env: JniEnvironment, input: jlong): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Long"), BoxMethods.longConstructor, input)
}

@PublishedApi
internal fun decodeBoxedLong(env: JniEnvironment, input: jobject): jlong {
    return env.callLongMethod(input, BoxMethods.longValue)
}

@PublishedApi
internal fun encodeBoxedInt(env: JniEnvironment, input: jint): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Integer"), BoxMethods.intConstructor, input)
}

@PublishedApi
internal fun decodeBoxedInt(env: JniEnvironment, input: jobject): jint {
    return env.callIntMethod(input, BoxMethods.intValue)
}

@PublishedApi
internal fun encodeBoxedByte(env: JniEnvironment, input: jbyte): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Byte"), BoxMethods.byteConstructor, input)
}

@PublishedApi
internal fun decodeBoxedByte(env: JniEnvironment, input: jobject): jbyte {
    return env.callByteMethod(input, BoxMethods.byteValue)
}

@PublishedApi
internal fun encodeBoxedBoolean(env: JniEnvironment, input: jboolean): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Boolean"), BoxMethods.boolConstructor, input)
}

@PublishedApi
internal fun decodeBoxedBoolean(env: JniEnvironment, input: jobject): jboolean {
    return env.callBooleanMethod(input, BoxMethods.boolValue)
}

@PublishedApi
internal fun encodeBoxedDouble(env: JniEnvironment, input: jdouble): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Double"), BoxMethods.doubleConstructor, input)
}

@PublishedApi
internal fun decodeBoxedDouble(env: JniEnvironment, input: jobject): jdouble {
    return env.callDoubleMethod(input, BoxMethods.doubleValue)
}

@PublishedApi
internal fun encodeBoxedFloat(env: JniEnvironment, input: jfloat): jobject {
    return env.newObject(ClassIds.get(env, "java.lang.Float"), BoxMethods.floatConstructor, input)
}

@PublishedApi
internal fun decodeBoxedFloat(env: JniEnvironment, input: jobject): jfloat {
    return env.callFloatMethod(input, BoxMethods.floatValue)
}
