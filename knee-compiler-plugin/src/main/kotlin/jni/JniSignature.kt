package io.deepmedia.tools.knee.plugin.compiler.jni

object JniSignature {

    fun get(type: JniType): String = buildString {
        appendJniType(type, isReturnType = true)
    }

    fun get(returnType: JniType, argumentTypes: List<JniType>): String = buildString {
        append('(')
        argumentTypes.forEach {
            appendJniType(it, isReturnType = false)
        }
        append(')')
        appendJniType(returnType, isReturnType = true)
    }

    /**
     * Table 3-2 Java VM Type Signatures
     * Z = boolean
     * B = byte
     * C = char
     * S = short
     * I = int
     * J = long
     * F = float
     * D = double
     * L<fully-qualified-class>; = class
     * [<type> = array type
     * (<args>)<return> = method
     */
    private fun StringBuilder.appendJniType(type: JniType, isReturnType: Boolean) {
        when (type) {
            is JniType.Void -> {
                require(isReturnType) { "JniType.Void is not allowed here." }
                append('V')
            }

            // PRIMITIVE TYPES
            is JniType.BooleanAsUByte -> append('Z')
            is JniType.Byte -> append('B')
            // builtIns.charType -> append('C')
            // builtIns.shortType -> append('S')
            is JniType.Int -> append('I')
            is JniType.Long -> append('J')
            is JniType.Float -> append('F')
            is JniType.Double -> append('D')

            // OBJECT TYPES
            is JniType.Object -> when (val arrayElement = type.arrayElement) {
                null -> {
                    append('L')
                    append(type.jvm.jvmClassName) // cares about dollar signs
                    append(';')
                }
                else -> {
                    append("[")
                    appendJniType(arrayElement, isReturnType)
                }
            }
        }
    }
}