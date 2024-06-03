package io.deepmedia.tools.knee.plugin.compiler.instances

import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.utils.map
import org.jetbrains.kotlin.name.Name

/**
 * Interface called 'Foo' becomes:
 * - KneeFoo (if not imported)
 * - KneeFoo$propertyName (if imported through property)
 */
object InterfaceNames {
    // val interfacePrefixMapper: (String) -> String = { "Knee$it" }

    private fun interfaceNameMapper(importInfo: ImportInfo?): (String) -> String {
        return { "Knee$it${importInfo?.let { info -> "$${info.id}" } ?: ""}" }
    }

    fun Name.asInterfaceName(importInfo: ImportInfo?): Name = map(block = interfaceNameMapper(importInfo))

    fun String.asInterfaceName(importInfo: ImportInfo?): String = interfaceNameMapper(importInfo).invoke(this)
}