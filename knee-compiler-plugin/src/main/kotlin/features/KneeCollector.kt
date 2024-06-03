package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.symbols.RuntimeIds
import io.deepmedia.tools.knee.plugin.compiler.symbols.AnnotationIds
import io.deepmedia.tools.knee.plugin.compiler.utils.simple
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.isPropertyAccessor
import org.jetbrains.kotlin.ir.visitors.*

class KneeCollector(module: IrModuleFragment) : IrElementVisitorVoid {


    val initializers = mutableListOf<KneeInitializer>()
    val modules = mutableListOf<KneeModule>()
    var hasDeclarations = false

    private val classes = mutableListOf<KneeClass>()
    private val enums = mutableListOf<KneeEnum>()
    private val interfaces = mutableListOf<KneeInterface>()

    private val importedClasses = mutableListOf<KneeClass>()
    private val importedEnums = mutableListOf<KneeEnum>()
    private val importedInterfaces = mutableListOf<KneeInterface>()

    // private val imports = mutableListOf<KneeImport>()
    private val topLevelDownwardFunctions = mutableListOf<KneeDownwardFunction>()
    private val topLevelDownwardProperties = mutableListOf<KneeDownwardProperty>()

    val allInterfaces get() = interfaces + importedInterfaces
            // + imports.flatMap { it.interfaces }

    val allEnums get() = enums + importedEnums
            // + imports.flatMap { it.enums }

    val allClasses get() = classes + importedClasses
            // + imports.flatMap { it.classes }

    val allDownwardProperties get() = topLevelDownwardProperties +
            allClasses.flatMap { it.properties } +
            allInterfaces.flatMap { it.downwardProperties }

    val allDownwardFunctions get() = topLevelDownwardFunctions +
            allDownwardProperties.flatMap { it.functions } +
            allClasses.flatMap { it.functions } +
            allInterfaces.flatMap { it.downwardFunctions }

    val allUpwardProperties get() =
        allInterfaces.flatMap { it.upwardProperties }

    val allUpwardFunctions get() =
        allUpwardProperties.flatMap { it.functions } +
        allInterfaces.flatMap { it.upwardFunctions }

    private val KneeClass.functions get() = constructors + members //  + disposer
    private val KneeDownwardProperty.functions get() = listOfNotNull(getter, setter)
    private val KneeUpwardProperty.functions get() = listOfNotNull(getter, setter)

    init {
        module.acceptVoid(this)
        // reconcileExpectActual(module)
    }


    /* @OptIn(ObsoleteDescriptorBasedAPI::class)
    private fun reconcileExpectActual(module: IrModuleFragment) {
        val allActuals = (allClasses + allDownwardFunctions)
            .filter { (it.source.descriptor as MemberDescriptor).isActual }
            .associateWith {
                val expects = it.source.descriptor.findExpects()
                check(expects.isNotEmpty()) { "$it marked as `actual` but could not find any corresponding `expect` declaration." }
                expects
            }
        val frontendToIr: MutableMap<MemberDescriptor, IrDeclarationWithName?> = allActuals
            .flatMap { it.value }
            .associateWithTo(mutableMapOf()) { null }
        module.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }
            override fun visitClass(declaration: IrClass) {
                if (declaration.descriptor in frontendToIr.keys) frontendToIr[declaration.descriptor] = declaration
                super.visitClass(declaration)
            }
            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                if (declaration.descriptor in frontendToIr.keys) frontendToIr[declaration.descriptor] = declaration
                super.visitSimpleFunction(declaration)
            }
        })

        allActuals.forEach { (knee, descriptors) ->
            knee.expectSources = descriptors.map {
                frontendToIr[it] ?: error("Could not find `actual` $it for Knee type $knee.")
            }
        }
    } */

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        /* if (declaration.hasAnnotation(kneeInitAnnotation)) {
            inits.add(KneeInit(declaration))
        } else */if (declaration.hasAnnotation(AnnotationIds.Knee)
            && declaration.isTopLevel
            && !declaration.isPropertyAccessor) {
            hasDeclarations = true
            topLevelDownwardFunctions.add(KneeDownwardFunction(declaration, null, null))
        }
        super.visitSimpleFunction(declaration)
    }

    override fun visitTypeAlias(declaration: IrTypeAlias) {
        if (declaration.hasAnnotation(AnnotationIds.KneeEnum)) {
            hasDeclarations = true
            val importInfo = ImportInfo(declaration.expandedType.simple("visitTypeAlias"), declaration)
            importedEnums.add(KneeEnum(declaration.expandedType.classOrFail.owner, importInfo))
        } else if (declaration.hasAnnotation(AnnotationIds.KneeClass)) {
            hasDeclarations = true
            val importInfo = ImportInfo(declaration.expandedType.simple("visitTypeAlias"), declaration)
            importedClasses.add(KneeClass(declaration.expandedType.classOrFail.owner, importInfo))
        } else if (declaration.hasAnnotation(AnnotationIds.KneeInterface)) {
            hasDeclarations = true
            val importInfo = ImportInfo(declaration.expandedType.simple("visitTypeAlias"), declaration)
            importedInterfaces.add(KneeInterface(declaration.expandedType.classOrFail.owner, importInfo))
        }
        super.visitTypeAlias(declaration)
    }

    override fun visitClass(declaration: IrClass) {
        if (declaration.hasAnnotation(AnnotationIds.KneeEnum)) {
            hasDeclarations = true
            enums.add(KneeEnum(declaration))
        } else if (declaration.hasAnnotation(AnnotationIds.KneeClass)) {
            hasDeclarations = true
            classes.add(KneeClass(declaration))
        } else if (declaration.hasAnnotation(AnnotationIds.KneeInterface)) {
            hasDeclarations = true
            interfaces.add(KneeInterface(declaration))
        } /* else if (declaration.hasAnnotation(kneeImportAnnotation)) {
            imports.add(KneeImport(declaration).also {
                hasDeclarations = hasDeclarations || it.classes.isNotEmpty() || it.enums.isNotEmpty() || it.interfaces.isNotEmpty()
            })
        } */ else if (declaration.kind == ClassKind.OBJECT && declaration.superClass?.classId == RuntimeIds.KneeModule) {
            modules.add(KneeModule(declaration))
        }/* else if ((declaration.descriptor as? MemberDescriptor)?.isActual == true) {
            allActualClasses.add(declaration)
        }*/
        super.visitClass(declaration)
    }

    override fun visitCall(expression: IrCall) {
        // Some functions throw at .callableId
        val callableId = runCatching { expression.symbol.owner.callableId }.getOrNull()
        if (callableId == RuntimeIds.initKnee) {
            initializers.add(KneeInitializer(expression))
        }
        super.visitCall(expression)
    }

    override fun visitProperty(declaration: IrProperty) {
        if (declaration.isTopLevel) {
            if (declaration.hasAnnotation(AnnotationIds.Knee)) {
                hasDeclarations = true
                topLevelDownwardProperties.add(KneeDownwardProperty(declaration, null))
            } else {
                // Old KneeModule detection
                /* val type = declaration.backingField?.type as? IrSimpleType
                if (type?.classOrNull?.owner?.classId == Names.runtimeKneeModuleClass) {
                    val initializer = declaration.backingField!!.initializer?.expression
                    if (initializer is IrConstructorCall) {
                        val publicConstructor = type.classOrFail.constructors.first { !it.owner.isPrimary }
                        if (initializer.symbol == publicConstructor) {
                            modules.add(KneeModule(declaration, initializer))
                        }
                    }
                } */
            }
        }
        super.visitProperty(declaration)
    }

    /* override fun visitTypeAlias(declaration: IrTypeAlias) {
        if (declaration.isActual) {
            allActualTypeAliases.add(declaration)
        }
        super.visitTypeAlias(declaration)
    } */
}