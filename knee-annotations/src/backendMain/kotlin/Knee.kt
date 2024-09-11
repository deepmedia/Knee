@file:Suppress("unused")

package io.deepmedia.tools.knee.annotations


@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    /**
     * Allows annotating a property with:
     * @property:Knee
     * val prop: Int = 42
     */
    AnnotationTarget.PROPERTY,
    /**
     * Allows annotating a property with:
     * @Knee
     * val prop: Int = 42
     * Like [AnnotationTarget.PROPERTY], the declaration can be found during visitIrProperty
     * so apparently we don't need special logic for this case.
     */
    AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class Knee

/**
 * This annotation is used internally only.
 */
@Retention(AnnotationRetention.BINARY)
annotation class KneeMetadata(val metadata: String)

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
annotation class KneeEnum(val name: String = "")

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
annotation class KneeClass(val name: String = "")

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
annotation class KneeObject(val name: String = "")

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
annotation class KneeInterface(val name: String = "")

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KneeRaw(val name: String)




