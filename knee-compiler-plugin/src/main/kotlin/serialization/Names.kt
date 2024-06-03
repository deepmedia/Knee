package io.deepmedia.tools.knee.plugin.compiler.serialization

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


object TypeNameSerializer : KSerializer<TypeName> {
    override val descriptor get() = TypeNameSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: TypeName) {
        encoder.encodeSerializableValue(TypeNameSurrogate.serializer(), TypeNameSurrogate(value))
    }
    override fun deserialize(decoder: Decoder): TypeName {
        return decoder.decodeSerializableValue(TypeNameSurrogate.serializer()).typeName
    }
}

@Serializable
private data class TypeNameSurrogate(
    val packageName: String,
    val simpleNames: List<String>,
    val typeArguments: List<TypeNameSurrogate>?
) {
    val typeName: TypeName get() = when (typeArguments) {
        null -> ClassName(packageName, simpleNames)
        else -> ClassName(packageName, simpleNames).parameterizedBy(typeArguments.map { it.typeName })
    }

    companion object {
        operator fun invoke(typeName: TypeName): TypeNameSurrogate = when (typeName) {
            is ClassName -> TypeNameSurrogate(typeName.packageName, typeName.simpleNames, null)
            is ParameterizedTypeName -> TypeNameSurrogate(typeName.rawType.packageName, typeName.rawType.simpleNames, typeName.typeArguments.map { TypeNameSurrogate(it) })
            is Dynamic, is LambdaTypeName, is TypeVariableName, is WildcardTypeName -> error("Unable to serialize this TypeName: $typeName")
        }
    }
}

object NameSerializer : KSerializer<Name> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "org.jetbrains.kotlin.name.Name",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): Name {
        return Name.guessByFirstCharacter(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Name) {
        encoder.encodeString(value.asString())
    }
}

object FqNameSerializer : KSerializer<FqName> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "org.jetbrains.kotlin.name.FqName",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): FqName {
        return FqName(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: FqName) {
        encoder.encodeString(value.asString())
    }
}