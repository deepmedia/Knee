package io.deepmedia.tools.knee.plugin.compiler.utils

import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf

// other utils

fun Name.asStringSafeForCodegen(firstLetterLowercase: Boolean): String {
    // KotlinPoet has a very restrictive regex for things added to CodeSpec.Builder.addNamed
    // and the input it.name can be extremely weird like "<set-?>".
    return asStringStripSpecialMarkers()
        .filter { it.isLetter() || it == '_' || it.isDigit() }
        .replaceFirstChar { if (firstLetterLowercase) it.lowercase() else it.uppercase() }
}

inline fun Name.map(special: Boolean = isSpecial, block: (String) -> String): Name {
    val name = block(asStringStripSpecialMarkers())
    return if (!special) Name.identifier(name) else Name.special("<$name>")
}

// IR => codegen name

val IrDeclarationWithName.codegenFqName: FqName
    get() = when (val parent = parent) {
        is IrDeclarationWithName -> parent.codegenFqName.child(codegenName)
        is IrPackageFragment -> parent.packageFqName.child(codegenName)
        else -> FqName(codegenName.asString())
    }

val IrType.codegenClassFqName: FqName?
    get() = classOrNull?.owner?.codegenFqName

val IrClass.codegenClassId: ClassId?
    get() = when (val parent = this.parent) {
        is IrClass -> parent.codegenClassId?.createNestedClassId(this.codegenName)
        is IrPackageFragment -> ClassId.topLevel(parent.packageFqName.child(this.codegenName))
        else -> null
    }


val IrDeclarationWithName.codegenFqNameWithoutPackageName: FqName
    get() = when (val parent = parent) {
        is IrDeclarationWithName -> parent.codegenFqNameWithoutPackageName.child(codegenName)
        is IrPackageFragment -> FqName(codegenName.asString())
        else -> error("Parent of $codegenName is invalid: $parent")
    }


@Suppress("UNCHECKED_CAST")
val IrDeclarationWithName.codegenName get(): Name {
    if (this is IrClass) {
        val e = getAnnotation(AnnotationIds.KneeClass)
            ?: getAnnotation(AnnotationIds.KneeEnum)
            ?: getAnnotation(AnnotationIds.KneeInterface)
            ?: return name
        val a = e.getValueArgument(Name.identifier("name")) ?: return name
        val str = (a as IrConst<String>).value.takeIf { it.isNotEmpty() } ?: return name
        return Name.identifier(str)
    }
    return name
}

inline fun IrDeclarationWithName.codegenUniqueName(special: Boolean = codegenName.isSpecial, map: (String) -> String): Name {
    val prefix = when (val p = parent) {
        is IrDeclarationWithName -> p.codegenFqNameWithoutPackageName.pathSegments().joinToString("_") {
            require(!it.isSpecial) { "Ancestor of $codegenName, $it has special characters. Not sure how to handle this." }
            it.asString()
        }
        is IrPackageFragment -> null
        else -> error("Parent of $codegenName is invalid: $parent")
    }
    return codegenName.map(special) {
        listOfNotNull(prefix, map(it)).joinToString("_")
    }
}

// FIR => codegen name

val DeclarationDescriptor.codegenName: Name
    get() {
    val e = annotations.findAnnotation(AnnotationIds.KneeClass)
        ?: annotations.findAnnotation(AnnotationIds.KneeEnum)
        ?: annotations.findAnnotation(AnnotationIds.KneeInterface)
        ?: return name
    val a = e.allValueArguments[Name.identifier("name")] ?: return name
    val str = (a as StringValue).value.takeIf { it.isNotEmpty() } ?: return name
    return Name.guessByFirstCharacter(str)
}

val DeclarationDescriptor.codegenFqName: FqName
    get() {
    val segments = parentsWithSelf.mapNotNull {
        when (it) {
            is ModuleDescriptor -> null
            is PackageFragmentDescriptor -> Name.identifier(it.fqName.asString())
            else -> codegenName
        }
    }.toList().reversed()
    return FqName(segments.joinToString(separator = "."))
}