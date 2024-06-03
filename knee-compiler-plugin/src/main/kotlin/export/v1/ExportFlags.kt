package io.deepmedia.tools.knee.plugin.compiler.export.v1

import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.BooleanValue

val IrClass.hasExport1Flag: Boolean get() {
    val e = getAnnotation(AnnotationIds.KneeClass)
        ?: getAnnotation(AnnotationIds.KneeEnum)
        ?: getAnnotation(AnnotationIds.KneeInterface)
        ?: return false

    val a = e.getValueArgument(Name.identifier("exported")) ?: return false
    @Suppress("UNCHECKED_CAST")
    return (a as? IrConst<Boolean>)?.value ?: false
}

val ClassDescriptor.hasExport1Flag: Boolean get() {
    val e = annotations.findAnnotation(AnnotationIds.KneeClass)
        ?: annotations.findAnnotation(AnnotationIds.KneeEnum)
        ?: annotations.findAnnotation(AnnotationIds.KneeInterface)
        ?: return false

    val arg = e.allValueArguments[Name.identifier("exported")] ?: return false
    return (arg as? BooleanValue)?.value ?: false
}