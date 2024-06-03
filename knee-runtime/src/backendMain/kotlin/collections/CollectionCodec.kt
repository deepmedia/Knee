package io.deepmedia.tools.knee.runtime.collections

import io.deepmedia.tools.knee.runtime.*
import io.deepmedia.tools.knee.runtime.compiler.ClassIds
import kotlinx.cinterop.*
import platform.android.JNI_ABORT
import platform.android.jarray
import platform.android.jobject

internal typealias CollectionBuilder<Element, Collection> = (size: Int, elementAt: (Int) -> Element) -> Collection

/**
 * Maps a jarray from/to an external element type, and from/into different kinds of collection types.
 * Use concrete subclasses:
 * - [JObjectCollectionCodec] when the element is a jobject
 * - one of the [SimpleCollectionCodec]s for primitive types
 * - a [TransformingCollectionCodec] if elements must be transformed on the fly
 */
internal interface CollectionCodec<Element: Any, Array: Any> {

    fun JniEnvironment.decodeIntoArray(array: jarray): Array
    fun JniEnvironment.decodeIntoList(array: jarray): List<Element>
    fun JniEnvironment.decodeIntoSet(array: jarray): Set<Element>

    fun JniEnvironment.encodeArray(array: Array): jarray
    fun JniEnvironment.encodeList(list: List<Element>): jarray
    fun JniEnvironment.encodeSet(set: Set<Element>): jarray
}

internal abstract class BaseCollectionCodec<Element: Any, ArrayType: Any>(
    protected val arraySpec: ArraySpec<ArrayType, Element>
) : CollectionCodec<Element, ArrayType> {

    final override fun JniEnvironment.decodeIntoArray(array: jarray): ArrayType {
        val length = getArrayLength(array).takeIf { it > 0 } ?: return arraySpec.empty
        return decodeIntoBuilder(array, length, arraySpec.builder)
    }

    final override fun JniEnvironment.decodeIntoList(array: jarray): List<Element> {
        val length = getArrayLength(array).takeIf { it > 0 } ?: return emptyList()
        return decodeIntoBuilder(array, length, ::List)
    }

    final override fun JniEnvironment.decodeIntoSet(array: jarray): Set<Element> {
        val length = getArrayLength(array).takeIf { it > 0 } ?: return emptySet()
        return decodeIntoBuilder(array, length) { size, itemAt ->
            buildSet { for (i in 0 until size) add(itemAt(i)) }
        }
    }

    // We could use a builder-like solution for encoding too - a jarray builder.
    // But primitive arrays have more efficient ways of filling data (setArrayRegion)
    // then iterating over the elements one by one, so we use instantiate + fill approach.

    final override fun JniEnvironment.encodeArray(array: ArrayType): jarray {
        val size = arraySpec.sizeOf(array)
        return instantiate(size).also { if (size > 0) fillFromArray(it, array) }
    }

    final override fun JniEnvironment.encodeList(list: List<Element>): jarray {
        return instantiate(list.size).also { if (list.isNotEmpty()) fillFromList(it, list) }
    }

    final override fun JniEnvironment.encodeSet(set: Set<Element>): jarray {
        return instantiate(set.size).also { if (set.isNotEmpty()) fillFromSet(it, set) }
    }

    protected abstract fun <Result> JniEnvironment.decodeIntoBuilder(array: jarray, length: Int, builder: CollectionBuilder<Element, Result>): Result
    protected abstract fun JniEnvironment.instantiate(size: Int): jarray
    protected abstract fun JniEnvironment.fillFromArray(jarray: jarray, array: ArrayType)
    protected abstract fun JniEnvironment.fillFromList(jarray: jarray, list: List<Element>)
    protected abstract fun JniEnvironment.fillFromSet(jarray: jarray, set: Set<Element>)
}

@PublishedApi
internal class JObjectCollectionCodec(private val jvmClassName: String): BaseCollectionCodec<jobject, Array<jobject>>(
    typedArraySpec()
) {

    override fun <Result> JniEnvironment.decodeIntoBuilder(array: jarray, length: Int, builder: CollectionBuilder<jobject, Result>): Result {
        return builder(length) { getObjectArrayElement(array, it) }
    }

    override fun JniEnvironment.instantiate(size: Int): jarray = newObjectArray(size,
        ClassIds.get(this, jvmClassName)
    )

    override fun JniEnvironment.fillFromArray(jarray: jarray, array: Array<jobject>) {
        array.forEachIndexed { index, obj -> setObjectArrayElement(jarray, index, obj) }
    }

    override fun JniEnvironment.fillFromList(jarray: jarray, list: List<jobject>) {
        list.forEachIndexed { index, obj -> setObjectArrayElement(jarray, index, obj) }
    }

    override fun JniEnvironment.fillFromSet(jarray: jarray, set: Set<jobject>) {
        set.forEachIndexed { index, obj -> setObjectArrayElement(jarray, index, obj) }
    }
}

@Suppress("unused")
@PublishedApi
internal sealed class SimpleCollectionCodec<Element: Any, ElementVar: CPrimitiveVar, ArrayType: Any>(
    arraySpec: ArraySpec<ArrayType, Element>,
    private val arrayToRef: ArrayType.(Int) -> CValuesRef<ElementVar>,
    private val read: CPointer<ElementVar>.(Int) -> Element,
    private val newInstance: JniEnvironment.(Int) -> jarray,
    private val setArrayRegion: JniEnvironment.(jarray, Int, Int, CPointer<ElementVar>) -> Unit
) : BaseCollectionCodec<Element, ArrayType>(arraySpec) {

    override fun <Result> JniEnvironment.decodeIntoBuilder(array: jarray, length: Int, builder: CollectionBuilder<Element, Result>): Result = usePrimitiveArrayCritical(array, JNI_ABORT) { handle ->
        val cast = handle.reinterpret<ElementVar>()
        builder(length) { cast.read(it) }
    }

    override fun JniEnvironment.instantiate(size: Int): jarray = newInstance(size)
    override fun JniEnvironment.fillFromList(jarray: jarray, list: List<Element>) = fillFromArray(jarray, arraySpec.build(list))
    override fun JniEnvironment.fillFromSet(jarray: jarray, set: Set<Element>) = fillFromArray(jarray, arraySpec.build(set))
    override fun JniEnvironment.fillFromArray(jarray: jarray, array: ArrayType) = memScoped {
        val pointer = array.arrayToRef(0).getPointer(this)
        setArrayRegion(jarray, 0, arraySpec.sizeOf(array), pointer)
    }
}

/** Converts [ByteArray] <-> [jarray] */
@PublishedApi
internal object ByteCollectionCodec : SimpleCollectionCodec<Byte, ByteVar, ByteArray>(ArraySpec.Bytes, ByteArray::refTo, { this[it] }, JniEnvironment::newByteArray, JniEnvironment::setByteArrayRegion)

/** Converts [IntArray] <-> [jarray] */
@PublishedApi
internal object IntCollectionCodec : SimpleCollectionCodec<Int, IntVar, IntArray>(ArraySpec.Ints, IntArray::refTo, { this[it] }, JniEnvironment::newIntArray, JniEnvironment::setIntArrayRegion)

/** Converts [LongArray] <-> [jarray] */
@PublishedApi
internal object LongCollectionCodec : SimpleCollectionCodec<Long, LongVar, LongArray>(ArraySpec.Longs, LongArray::refTo, { this[it] }, JniEnvironment::newLongArray, JniEnvironment::setLongArrayRegion)

/** Converts [FloatArray] <-> [jarray] */
@PublishedApi
internal object FloatCollectionCodec : SimpleCollectionCodec<Float, FloatVar, FloatArray>(ArraySpec.Floats, FloatArray::refTo, { this[it] }, JniEnvironment::newFloatArray, JniEnvironment::setFloatArrayRegion)

/** Converts [DoubleArray] <-> [jarray] */
@PublishedApi
internal object DoubleCollectionCodec : SimpleCollectionCodec<Double, DoubleVar, DoubleArray>(ArraySpec.Doubles, DoubleArray::refTo, { this[it] }, JniEnvironment::newDoubleArray, JniEnvironment::setDoubleArrayRegion)

/** Converts [UByteArray] <-> [jarray]. Used for booleans */
@PublishedApi
internal object UByteCollectionCodec : SimpleCollectionCodec<UByte, UByteVar, UByteArray>(ArraySpec.UBytes, UByteArray::refTo, { this[it] }, JniEnvironment::newBooleanArray, JniEnvironment::setBooleanArrayRegion)

@PublishedApi
internal class TransformingCollectionCodec<EncodedElement: Any, DecodedElement: Any, DecodedArray: Any>(
    private val source: CollectionCodec<EncodedElement, *>,
    private val decodedArraySpec: ArraySpec<DecodedArray, DecodedElement>,
    private val decodeElement: (EncodedElement) -> DecodedElement,
    private val encodeElement: (DecodedElement) -> EncodedElement
) : CollectionCodec<DecodedElement, DecodedArray> {

    // TODO: encoded element might be a jobject. We need to make room for the references
    //  both when encoding and when decoding, or use a Sequence-like object and call deleteLocalRef after mapping

    override fun JniEnvironment.decodeIntoList(array: jarray): List<DecodedElement> {
        val encodedList = with(source) { decodeIntoList(array) }
        return encodedList.map(decodeElement)
    }

    override fun JniEnvironment.decodeIntoSet(array: jarray): Set<DecodedElement> {
        val encodedSet = with(source) { decodeIntoSet(array) }
        return encodedSet.mapTo(mutableSetOf(), decodeElement)
    }

    override fun JniEnvironment.decodeIntoArray(array: jarray): DecodedArray =
        decodedArraySpec.build(decodeIntoList(array))

    override fun JniEnvironment.encodeList(list: List<DecodedElement>): jarray {
        return with(source) { encodeList(list.map(encodeElement)) }
    }

    override fun JniEnvironment.encodeSet(set: Set<DecodedElement>): jarray {
        return with(source) { encodeList(set.map(encodeElement)) }
    }

    override fun JniEnvironment.encodeArray(array: DecodedArray): jarray {
        val list = List(decodedArraySpec.sizeOf(array)) { encodeElement(decodedArraySpec.elementOf(array, it)) }
        return with(source) { encodeList(list) }
    }
}
