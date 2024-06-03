package io.deepmedia.tools.knee.plugin.compiler.serialization

import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

fun IrClassListSerializer(symbols: KneeSymbols) = ListSerializer(IrClassSerializer(symbols))

class IrClassSerializer(private val symbols: KneeSymbols) : KSerializer<IrClass> {
    override val descriptor: SerialDescriptor get() = ClassIdSerializer.descriptor
    override fun serialize(encoder: Encoder, value: IrClass) {
        encoder.encodeSerializableValue(ClassIdSerializer, value.classIdOrFail)
    }
    override fun deserialize(decoder: Decoder): IrClass {
        return symbols.klass(decoder.decodeSerializableValue(ClassIdSerializer)).owner
    }
}

object ClassIdSerializer : KSerializer<ClassId> {
    override val descriptor get() = ClassIdSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: ClassId) {
        encoder.encodeSerializableValue(ClassIdSurrogate.serializer(), ClassIdSurrogate(value))
    }
    override fun deserialize(decoder: Decoder): ClassId {
        return decoder.decodeSerializableValue(ClassIdSurrogate.serializer()).classId
    }
}

@Serializable
private data class ClassIdSurrogate(
    @Serializable(with = FqNameSerializer::class) private val packageFqName: FqName,
    @Serializable(with = FqNameSerializer::class) private val relativeClassName: FqName,
    private val isLocal: Boolean
) {
    constructor(classId: ClassId) : this(classId.packageFqName, classId.relativeClassName, classId.isLocal)
    // constructor(klass: IrClass) : this(klass.classIdOrFail)
    val classId get() = ClassId(packageFqName, relativeClassName, isLocal)
    // fun klass(symbols: KneeSymbols): IrClass = symbols.klass(classId).owner
}