package io.deepmedia.tools.knee.plugin.compiler.export.v2

import io.deepmedia.tools.knee.plugin.compiler.codec.Codec
import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenType
import io.deepmedia.tools.knee.plugin.compiler.jni.JniType
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType


@Serializable
data class ExportedTypeInfo(
    val id: Int, // unique within the same module
    @Contextual val localIrType: IrSimpleType,
    val localCodegenType: CodegenType,
    val encodedType: JniType.Real
) {
    constructor(id: Int, codec: Codec) : this(
        id = id,
        localIrType = codec.localIrType,
        localCodegenType = codec.localCodegenType,
        encodedType = checkNotNull(codec.encodedType as? JniType.Real) {
            "Can't export ${codec.localIrType}, its jni representation is not JniType.Real"
        }
    )
    // val uniqueId: Int get() = localIrType.disambiguationHash()
}