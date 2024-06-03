@file:Suppress("unused")

package io.deepmedia.tools.knee.runtime.collections

/**
 * Sealed class for all of the existing array types e.g. [Array], [IntArray], [ByteArray], [LongArray],
 * which unfortunately do not have a common super class.
 */
@PublishedApi
internal sealed class ArraySpec<Type: Any, Element: Any>(
    val empty: Type, 
    val builder: CollectionBuilder<Element, Type> // a builder that can create 'Type'
) {
    abstract fun sizeOf(array: Type): Int
    abstract fun elementOf(array: Type, index: Int): Element
    
    fun build(from: Collection<Element>): Type {
        val iterator = from.iterator()
        return builder(from.size) { iterator.next() }
    }

    object Bytes : ArraySpec<ByteArray, Byte>(ByteArray(0), ::ByteArray) {
        override fun sizeOf(array: ByteArray) = array.size
        override fun elementOf(array: ByteArray, index: Int) = array[index]
        // override fun build(from: Collection<Byte>) = from.toByteArray()
    }

    object Booleans : ArraySpec<BooleanArray, Boolean>(BooleanArray(0), ::BooleanArray) {
        override fun sizeOf(array: BooleanArray) = array.size
        override fun elementOf(array: BooleanArray, index: Int) = array[index]
        // override fun build(from: Collection<Boolean>) = from.toBooleanArray()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    object UBytes : ArraySpec<UByteArray, UByte>(UByteArray(0), ::UByteArray) {
        override fun sizeOf(array: UByteArray) = array.size
        override fun elementOf(array: UByteArray, index: Int) = array[index]
        // override fun build(from: Collection<UByte>) = from.toUByteArray()
    }

    object Ints : ArraySpec<IntArray, Int>(IntArray(0), ::IntArray) {
        override fun sizeOf(array: IntArray) = array.size
        override fun elementOf(array: IntArray, index: Int) = array[index]
        // override fun build(from: Collection<Int>) = from.toIntArray()
    }

    object Longs : ArraySpec<LongArray, Long>(LongArray(0), ::LongArray) {
        override fun sizeOf(array: LongArray) = array.size
        override fun elementOf(array: LongArray, index: Int) = array[index]
        // override fun build(from: Collection<Long>) = from.toLongArray()
    }

    object Floats : ArraySpec<FloatArray, Float>(FloatArray(0), ::FloatArray) {
        override fun sizeOf(array: FloatArray) = array.size
        override fun elementOf(array: FloatArray, index: Int) = array[index]
        // override fun build(from: Collection<Float>) = from.toFloatArray()
    }

    object Doubles : ArraySpec<DoubleArray, Double>(DoubleArray(0), ::DoubleArray) {
        override fun sizeOf(array: DoubleArray) = array.size
        override fun elementOf(array: DoubleArray, index: Int) = array[index]
        // override fun build(from: Collection<Double>) = from.toDoubleArray()
    }

    class Typed<Element : Any>(
        empty: Array<Element>,
        builder: CollectionBuilder<Element, Array<Element>>,
        // private val maker: Collection<Element>.() -> Array<Element>
    ) : ArraySpec<Array<Element>, Element>(empty, builder) {
        override fun sizeOf(array: Array<Element>) = array.size
        override fun elementOf(array: Array<Element>, index: Int) = array[index]
        // override fun build(from: Collection<Element>) = from.maker()
    }
}

@PublishedApi
internal inline fun <reified Element : Any> typedArraySpec(): ArraySpec.Typed<Element> {
    return ArraySpec.Typed<Element>(emptyArray(), ::Array) // { toTypedArray() }
}
