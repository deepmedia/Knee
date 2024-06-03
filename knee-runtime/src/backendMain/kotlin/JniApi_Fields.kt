package io.deepmedia.tools.knee.runtime

import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import platform.android.jobject
import platform.android.jfieldID
import platform.android.jboolean
import platform.android.jchar

// TODO: setters

fun JniEnvironment.getIntField(jobject: jobject, field: jfieldID): Int {
    return api.GetIntField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getBooleanField(jobject: jobject, field: jfieldID): jboolean {
    return api.GetBooleanField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getLongField(jobject: jobject, field: jfieldID): Long {
    return api.GetLongField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getFloatField(jobject: jobject, field: jfieldID): Float {
    return api.GetFloatField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getDoubleField(jobject: jobject, field: jfieldID): Double {
    return api.GetDoubleField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getShortField(jobject: jobject, field: jfieldID): Short {
    return api.GetShortField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getByteField(jobject: jobject, field: jfieldID): Byte {
    return api.GetByteField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getCharField(jobject: jobject, field: jfieldID): jchar {
    return api.GetCharField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getObjectField(jobject: jobject, field: jfieldID): jobject? {
    return api.GetObjectField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticIntField(jobject: jobject, field: jfieldID): Int {
    return api.GetStaticIntField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticBooleanField(jobject: jobject, field: jfieldID): jboolean {
    return api.GetStaticBooleanField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticLongField(jobject: jobject, field: jfieldID): Long {
    return api.GetStaticLongField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticFloatField(jobject: jobject, field: jfieldID): Float {
    return api.GetStaticFloatField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticDoubleField(jobject: jobject, field: jfieldID): Double {
    return api.GetStaticDoubleField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticShortField(jobject: jobject, field: jfieldID): Short {
    return api.GetStaticShortField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticByteField(jobject: jobject, field: jfieldID): Byte {
    return api.GetStaticByteField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticCharField(jobject: jobject, field: jfieldID): jchar {
    return api.GetStaticCharField!!(this, jobject, field).also { checkPendingJvmException() }
}

fun JniEnvironment.getStaticObjectField(jobject: jobject, field: jfieldID): jobject? {
    return api.GetStaticObjectField!!(this, jobject, field).also { checkPendingJvmException() }
}