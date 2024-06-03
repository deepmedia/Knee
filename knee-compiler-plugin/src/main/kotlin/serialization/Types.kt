package io.deepmedia.tools.knee.plugin.compiler.serialization

import io.deepmedia.tools.knee.plugin.compiler.symbols.KneeSymbols
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.types.Variance

class IrSimpleTypeSerializer(private val symbols: KneeSymbols) : KSerializer<IrSimpleType> {
    override val descriptor get() = IrSimpleTypeSurrogate.serializer().descriptor
    override fun serialize(encoder: Encoder, value: IrSimpleType) {
        encoder.encodeSerializableValue(IrSimpleTypeSurrogate.serializer(), IrSimpleTypeSurrogate(value))
    }
    override fun deserialize(decoder: Decoder): IrSimpleType {
        return decoder.decodeSerializableValue(IrSimpleTypeSurrogate.serializer()).simpleType(symbols)
    }
}

@Serializable
private data class IrSimpleTypeSurrogate(
    private val nullable: Boolean,
    private val typeArguments: List<IrSimpleTypeSurrogate>,
    @Contextual private val classRef: IrClass
) {
    constructor(type: IrSimpleType) : this(
        nullable = type.isNullable(),
        classRef = type.classOrFail.owner,
        typeArguments = type.arguments.map {
            check(it is IrTypeProjection) { "Type arguments should be IrTypeProjection, was: $it" }
            check(it.variance == Variance.INVARIANT) { "Type arguments variance should be INVARIANT, was: ${it.variance}" }
            val simpleType = checkNotNull(it.type as IrSimpleType) { "Type arguments should be IrSimpleType, was: ${it.type}" }
            IrSimpleTypeSurrogate(simpleType)
            // val klass = checkNotNull(it.type.classOrNull) { "Type arguments should be classes, was: ${it.type.dumpKotlinLike()}" }
            // ModuleMetadataClass(klass.owner)
        }
    )
    fun simpleType(symbols: KneeSymbols): IrSimpleType {
        // val args = typeArguments.map { it.klass(symbols).defaultType }.toTypedArray()
        val args = typeArguments.map { it.simpleType(symbols) }.toTypedArray()
        val res = classRef.typeWith(*args)
        return res.withNullability(nullable)
    }
}