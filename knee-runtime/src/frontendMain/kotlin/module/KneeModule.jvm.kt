package io.deepmedia.tools.knee.runtime.module


@Suppress("unused")
abstract class KneeModule {

    protected abstract val exportAdapters: Map<Int, Adapter<*, *>>

    @Suppress("UNCHECKED_CAST")
    fun <Encoded, Decoded> getExportAdapter(typeId: Int): Adapter<Encoded, Decoded> {
        val adapter = checkNotNull(exportAdapters[typeId]) { "No adapter for type: $typeId" }
        return adapter as Adapter<Encoded, Decoded>
    }

    class Adapter<Encoded, Decoded>(
        private val encoder: (decoded: Decoded) -> Encoded,
        private val decoder: (encoded: Encoded) -> Decoded
    ) {
        fun encode(decoded: Decoded): Encoded = encoder(decoded)
        fun decode(encoded: Encoded): Decoded = decoder(encoded)
    }
}