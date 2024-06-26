package io.deepmedia.tools.knee.plugin.compiler.features

import io.deepmedia.tools.knee.plugin.compiler.codegen.CodegenClass
import io.deepmedia.tools.knee.plugin.compiler.functions.UpwardFunctionSignature
import io.deepmedia.tools.knee.plugin.compiler.import.ImportInfo
import io.deepmedia.tools.knee.plugin.compiler.utils.requireNotComplex
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.*


class KneeInterface(
    source: IrClass,
    val importInfo: ImportInfo? = null
) : KneeFeature<IrClass>(source, "KneeInterface") {

    val downwardFunctions: List<KneeDownwardFunction>
    val upwardFunctions: List<KneeUpwardFunction>

    val downwardProperties: List<KneeDownwardProperty>
    val upwardProperties: List<KneeUpwardProperty>

    init {
        source.requireNotComplex(this, ClassKind.INTERFACE,
            typeArguments = importInfo?.type?.arguments ?: emptyList()
        )

        val members = source.functions
            .filter { it.dispatchReceiverParameter != null } // drop static functions
            .filter { !it.isPropertyAccessor } // drop property getters and setters
            // .filter { it.isFakeOverride } // drop equals, hashCode, toString
            // ^ We can't do this. A function declared in some parent appears in this class as a fake override
            // Just like equals, hashCode and toString do. A better filter is to take only abstract functions.
            .filter { it.modality == Modality.ABSTRACT }
            .onEach { it.requireNotComplex("$this member ${it.name}", allowSuspend = true) }
            .toList()

        val properties = source.properties
            .toList()

        this.downwardFunctions = members.map { KneeDownwardFunction(it, parentInstance = this, parentProperty = null) }
        this.downwardProperties = properties.map { KneeDownwardProperty(it, parentInstance = this) }
        this.upwardFunctions = members.map { KneeUpwardFunction(it, parentInterface = this) }
        this.upwardProperties = properties.map { KneeUpwardProperty(it, parentInterface = this) }
    }

    /**
     * The interface implementation generated by Interfaces.kt.
     */
    lateinit var irImplementation: IrClass
    lateinit var codegenImplementation: CodegenClass
    var codegenClone: CodegenClass? = null

    lateinit var irGetVirtualMachine: IrBuilderWithScope.() -> IrExpression
    lateinit var irGetMethodOwner: IrBuilderWithScope.() -> IrExpression
    lateinit var irGetJvmObject: IrBuilderWithScope.() -> IrExpression
    lateinit var irGetMethod: IrBuilderWithScope.(UpwardFunctionSignature) -> IrExpression

    // This was written thinking that super class functions are not present in the interface if it doesn't redeclare them.
    // That's not true, the simple 'functions' helper is already enough so there's nothing to do.
    /* private fun IrClass.allFunctions(reject: MutableSet<IrSimpleFunction> = mutableSetOf()): Sequence<IrSimpleFunction> {
        return sequence {
            val own = functions.filter { it !in reject }.toMutableList()
            yieldAll(own)
            reject.addAll(own.flatMap { it.allOverridden() })
            val superClasses = superTypes.mapNotNull { it.classOrNull?.owner }
            superClasses.forEach { yieldAll(it.allFunctions(reject)) }
        }
    } */
}