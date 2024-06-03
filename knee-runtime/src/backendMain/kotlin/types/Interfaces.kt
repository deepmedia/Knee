package io.deepmedia.tools.knee.runtime.types

import io.deepmedia.tools.knee.runtime.JniEnvironment
import io.deepmedia.tools.knee.runtime.compiler.ClassIds
import io.deepmedia.tools.knee.runtime.compiler.JvmInterfaceWrapper
import io.deepmedia.tools.knee.runtime.getObjectClass
import io.deepmedia.tools.knee.runtime.isSameObject
import kotlinx.cinterop.*
import platform.android.jlong
import platform.android.jobject


@Suppress("unused")
@PublishedApi
internal fun <T: Any> encodeInterface(environment: JniEnvironment, interface_: T): jobject {
    return if (interface_ is JvmInterfaceWrapper<*>) {
        interface_.jvmInterfaceObject
    } else {
        val address: jlong = StableRef.create(interface_).asCPointer().toLong()
        encodeBoxedLong(environment, address)
    }
}

@Suppress("unused")
internal inline fun <reified T: Any> decodeInterface(
    environment: JniEnvironment,
    interface_: jobject,
    wrapper: () -> JvmInterfaceWrapper<T>
): T {
    val interfaceClass = environment.getObjectClass(interface_)
    val longClass = ClassIds.get(environment, "java.lang.Long")
    return if (environment.isSameObject(interfaceClass, longClass)) {
        val longValue = decodeBoxedLong(environment, interface_)
        longValue.toCPointer<CPointed>()!!.asStableRef<T>().get()
    } else {
        wrapper() as T
    }
}