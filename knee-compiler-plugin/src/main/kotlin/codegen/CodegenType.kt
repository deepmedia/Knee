package io.deepmedia.tools.knee.plugin.compiler.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import io.deepmedia.tools.knee.plugin.compiler.serialization.TypeNameSerializer
import io.deepmedia.tools.knee.plugin.compiler.utils.asTypeName
import io.deepmedia.tools.knee.plugin.compiler.utils.codegenClassId
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

@Serializable
sealed class CodegenType {

    abstract val name: TypeName

    @Serializable
    data class IrBased(@Contextual val irType: IrSimpleType) : CodegenType() {
        override val name: TypeName by lazy { irType.asTypeName() }
    }

    @Serializable
    data class KpBased(@Serializable(with = TypeNameSerializer::class) override val name: TypeName) : CodegenType()

    override fun equals(other: Any?): Boolean {
        if (other !is CodegenType) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    // my/class/name/OuterClass$InnerClass
    // Need special logic for String and possibly other types that I'm missing at the moment
    val jvmClassName: String get() {
        val name = when (this) {
            is IrBased -> {
                val elementClassId = requireNotNull(irType.classOrNull?.owner?.codegenClassId) {
                    "Invalid CodegenType $irType (no fq name)"
                }
                JvmClassName.byClassId(elementClassId)
            }
            is KpBased -> {
                val kpClass: ClassName = when (val type = name) {
                    is ClassName -> type
                    is ParameterizedTypeName -> type.rawType
                    else -> error("Unsupported kotlinpoet type: $type")
                }
                val dotsAndDollars = kpClass.reflectionName() // dots + dollar sign
                JvmClassName.byFqNameWithoutInnerClasses(dotsAndDollars)
            }
        }.internalName
        return when {
            name == "kotlin/String" -> "java/lang/String"
            name == "kotlin/Any" -> "java/lang/Object"
            // Lambdas do not exist on the JVM. kotlin/FunctionX => kotlin/jvm/functions/FunctionX
            name.startsWith("kotlin/Function") -> "kotlin/jvm/functions/Function${name.drop(15).toInt()}"
            // Suspend lambdas do not exist either. kotlin/coroutines/SuspendFunctionX => kotlin/jvm/functions/Function(X+1)
            // The extra parameter is for the continuation.
            name.startsWith("kotlin/coroutines/SuspendFunction") -> "kotlin/jvm/functions/Function${name.drop(33).toInt() + 1}"
            else -> name
        }
    }

    companion object {
        fun from(irType: IrSimpleType): CodegenType = IrBased(irType)
        fun from(fqName: String): CodegenType = KpBased(ClassName.bestGuess(fqName))
        fun from(fqName: FqName): CodegenType = KpBased(ClassName.bestGuess(fqName.asString()))
        fun from(poetType: TypeName): CodegenType = KpBased(poetType)
    }
}