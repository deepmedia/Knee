package io.deepmedia.tools.knee.plugin.compiler.jni

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import io.deepmedia.tools.knee.plugin.compiler.symbols.KotlinIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.PlatformIds
import io.deepmedia.tools.knee.plugin.compiler.utils.simpleName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.ir.types.*

/**
 * All possible types that can pass the JNI interface.
 * https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/types.html
 * Note that conversion between [knOrNull] and [jvmOrNull] is done automatically by the JNI.
 */
@Serializable
sealed interface JniType {

    val knOrNull: IrSimpleType? get() = when (this) {
        is Real -> kn
        else -> null
    }

    val jvmOrNull: CodegenType? get() = when (this) {
        is Real -> jvm
        else -> null
    }

    /** A very special type, allowed only in return types of function, not to be used elsewhere */
    @Serializable
    object Void : JniType

    @Serializable
    sealed interface Real : JniType {
        val kn: IrSimpleType
        val jvm: CodegenType
    }

    @Serializable
    sealed interface Primitive : Real {
        // local simple names: for most primitives they are the same, but for some they aren't.
        // E.g. for JniType.Boolean, jvm = "Boolean" and kn = "UByte"
        val jvmSimpleName get() = jvm.name.simpleName
        val knSimpleName get() = kn.let { CodegenType.from(it) }.name.simpleName
        fun array(symbols: KneeSymbols): Array
    }

    @Serializable
    class Int private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.builtIns.intType as IrSimpleType)
        override val jvm get() = CodegenType.from(INT)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(INT_ARRAY), Int(symbols))
        }
    }

    @Serializable
    class Float private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.builtIns.floatType as IrSimpleType)
        override val jvm get() = CodegenType.from(FLOAT)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(FLOAT_ARRAY), Float(symbols))
        }
    }

    @Serializable
    class Double private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.builtIns.doubleType as IrSimpleType)
        override val jvm get() = CodegenType.from(DOUBLE)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(DOUBLE_ARRAY), Double(symbols))
        }
    }

    @Serializable
    class Long private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.builtIns.longType as IrSimpleType)
        override val jvm get() = CodegenType.from(LONG)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(LONG_ARRAY), Long(symbols))
        }
    }

    @Serializable
    class Byte private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.builtIns.byteType as IrSimpleType)
        override val jvm get() = CodegenType.from(BYTE)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(BYTE_ARRAY), Byte(symbols))
        }
    }

    // The name makes it immediately clear that the types at the two ends are different
    @Serializable
    class BooleanAsUByte private constructor(@Contextual override val kn: IrSimpleType) : Primitive {
        constructor(symbols: KneeSymbols) : this(kn = symbols.klass(KotlinIds.UByte).defaultType as IrSimpleType)
        override val jvm get() = CodegenType.from(BOOLEAN)
        override fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(BOOLEAN_ARRAY), BooleanAsUByte(symbols))
        }
    }

    @Serializable
    class Object private constructor(@Contextual override val kn: IrSimpleType, override val jvm: CodegenType) : Real {
        constructor(symbols: KneeSymbols, jvm: CodegenType) : this(
            kn = symbols.typeAliasUnwrapped(PlatformIds.jobject) as IrSimpleType,
            jvm = jvm
        )
        fun array(symbols: KneeSymbols): Array {
            return Array(symbols, CodegenType.from(ARRAY.parameterizedBy(jvm.name)), Object(symbols, jvm))
        }
    }

    // Can be array of primitive or array of object
    @Serializable
    class Array private constructor(
        @Contextual override val kn: IrSimpleType,
        override val jvm: CodegenType,
        val element: Real
    ) : Real {
        constructor(symbols: KneeSymbols, jvm: CodegenType, element: Real) : this(
            symbols.typeAliasUnwrapped(PlatformIds.jobjectArray) as IrSimpleType, jvm, element
        )
    }
}