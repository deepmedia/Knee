package io.deepmedia.tools.knee.plugin.compiler

import io.deepmedia.tools.knee.plugin.compiler.export.v1.hasExport1Flag
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportFirDescriptors
import io.deepmedia.tools.knee.plugin.compiler.export.v1.ExportInfo
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider

/**
 * Needed for K1 exports, unused now.
 */
class KneeSyntheticResolve : SyntheticResolveExtension {
    private val exportFirCache = mutableMapOf<ClassDescriptor, ExportFirDescriptors?>()

    private fun getExportFirOfAdapter(adapter: ClassDescriptor): ExportFirDescriptors? {
        return exportFirCache.values.firstOrNull { it?.adapterDescriptor == adapter }
    }

    private fun getExportFirOfClass(exportedClass: ClassDescriptor): ExportFirDescriptors? {
        return exportFirCache.getOrPut(exportedClass) {
            if (!exportedClass.hasExport1Flag) return@getOrPut null
            ExportFirDescriptors(exportedClass)
        }
    }


    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        var exportDescriptor = getExportFirOfClass(thisDescriptor)
        if (exportDescriptor != null) {
            return listOf(exportDescriptor.annotatedFunctionName)
        }
        exportDescriptor = getExportFirOfAdapter(thisDescriptor)
        if (exportDescriptor != null) {
            return exportDescriptor.adapterFunctionNames
        }
        return super.getSyntheticFunctionNames(thisDescriptor)
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> {
        val exportDescriptor = getExportFirOfClass(thisDescriptor)
        return when (val location = exportDescriptor?.exportInfo?.adapterNativeCoordinates) {
            null -> super.getSyntheticNestedClassNames(thisDescriptor)
            is ExportInfo.NativeCoordinates.InnerObject -> listOf(location.name)
        }
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        var exportDescriptor = getExportFirOfClass(thisDescriptor)
        if (exportDescriptor?.annotatedFunctionName == name) {
            result.add(exportDescriptor.makeAnnotatedFunctionDescriptor())
            return
        }
        exportDescriptor = getExportFirOfAdapter(thisDescriptor)
        if (exportDescriptor != null && name in exportDescriptor.adapterFunctionNames) {
            result.add(exportDescriptor.makeAdapterFunctionDescriptor(thisDescriptor, name))
            return
        }
        super.generateSyntheticMethods(thisDescriptor, name, bindingContext, fromSupertypes, result)
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        val exportDescriptor = getExportFirOfClass(thisDescriptor)
        when (val location = exportDescriptor?.exportInfo?.adapterNativeCoordinates) {
            null -> {}
            is ExportInfo.NativeCoordinates.InnerObject -> {
                if (location.name == name) {
                    result.add(exportDescriptor.makeAdapterDescriptor(ctx, declarationProvider, name))
                }
            }
        }
        super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
    }
}
