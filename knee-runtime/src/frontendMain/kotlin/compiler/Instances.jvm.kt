@file:JvmName("InstancesKt")
@file:Suppress("RedundantVisibilityModifier")

package io.deepmedia.tools.knee.runtime.compiler

@Suppress("Unused")
public external fun kneeDisposeInstance(ref: Long)

@Suppress("Unused")
public external fun kneeHashInstance(ref: Long): Int

@Suppress("Unused")
public external fun kneeCompareInstance(ref0: Long, ref1: Long): Boolean

@Suppress("Unused")
public external fun kneeDescribeInstance(ref: Long): String

// Turns out we don't need these. We just act from JNI which has no access control

/* @Suppress("Unused")
public fun kneeUnwrapInstance(instance: Any): Long {
    return try {
        val field = instance.javaClass.getDeclaredField("\$knee")
        field.isAccessible = true
        field.getLong(instance)
    } catch (e: Throwable) {
        0L
    }
}

@Suppress("Unused")
public fun kneeWrapInstance(ref: Long, className: String): Any? {
    return try {
        Class.forName(className).getDeclaredConstructor(Long::class.java).newInstance(ref)
    } catch (e: Throwable) {
        null
    }
} */