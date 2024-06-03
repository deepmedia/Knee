package io.deepmedia.tools.knee.plugin.compiler.codegen

import com.squareup.kotlinpoet.*
import io.deepmedia.tools.knee.plugin.compiler.utils.canonicalName
import io.deepmedia.tools.knee.plugin.compiler.utils.disambiguationName
import java.lang.IllegalStateException

sealed class CodegenDeclaration<T: Any> constructor(val spec: T) {

    companion object {
        private val RepeatableUids = listOf("constructor()")
    }

    private val mutableChildren = mutableListOf<CodegenDeclaration<*>>()
    val children: List<CodegenDeclaration<*>> get() = mutableChildren

    val descendants: Sequence<CodegenDeclaration<*>> get() {
        return sequence {
            yield(this@CodegenDeclaration)
            yieldAll(children.asSequence().flatMap { it.descendants })
        }
    }

    abstract val uid: String

    abstract val packageName: String

    abstract val modifiers: List<KModifier>

    abstract override fun toString(): String

    inline fun <reified C: CodegenDeclaration<*>> addChildIfNeeded(item: C): C {
        val existing = children.firstOrNull { it.uid == item.uid }
        return if (existing != null) { existing as C } else { item.also { addChild(it) } }
    }

    fun addChild(item: CodegenDeclaration<*>) {
        require(item.uid in RepeatableUids || children.none { it.uid == item.uid }) {
            val others = children.filter { it.uid == item.uid }
            "Already have item with id '${item.uid}'. Use addChildIfNeeded?\n\titem=$item\n\texisting=$others\n\tself=$this"
        }
        mutableChildren.add(item)
        item.onAddedToParent(this)
    }

    fun addChildren(vararg items: CodegenDeclaration<*>) {
        items.forEach { addChild(it) }
    }

    protected open fun onAddedToParent(parent: CodegenDeclaration<*>) {}
}

class CodegenFile(spec: FileSpec.Builder) : CodegenDeclaration<FileSpec.Builder>(spec) {
    override val uid by lazy { "File(${spec.name})" }
    override val modifiers = emptyList<KModifier>()
    override fun toString() = spec.build().toString()
    override val packageName: String = spec.packageName
    val fileName: String = spec.name // without .kt extension
}

class CodegenFunction(spec: FunSpec.Builder, val isPrimaryConstructor: Boolean = false) : CodegenDeclaration<FunSpec.Builder>(spec) {
    override val uid by lazy {
        "Fun(${spec.build().name}, ${spec.parameters.joinToString { parameterSpec ->  
            parameterSpec.type.disambiguationName
        }})"
    }

    override val modifiers get() = spec.modifiers

    override fun toString() = spec.build().toString()

    val isGetter get() = spec.build().name == FunSpec.getterBuilder().build().name
    val isSetter get() = spec.build().name == FunSpec.setterBuilder().build().name

    override lateinit var packageName: String
        private set

    override fun onAddedToParent(parent: CodegenDeclaration<*>) {
        super.onAddedToParent(parent)
        packageName = parent.packageName
    }
}

class CodegenClass(spec: TypeSpec.Builder) : CodegenDeclaration<TypeSpec.Builder>(spec) {
    private fun TypeSpec.Builder.tempBuild(): TypeSpec {
        return try { build() } catch (e: Throwable) {
            if (e.message?.contains("Functional interfaces must have exactly one abstract function. Contained 0: []") == true) {
                val tempFun = FunSpec.builder("FAKEFUNCTION")
                    .addModifiers(KModifier.ABSTRACT)
                    .build()
                addFunction(tempFun)
                build().also { funSpecs.remove(tempFun) }
            } else throw e
        }
    }
    override val uid: String by lazy {
        val build = spec.tempBuild()
        var name = when {
            build.name != null -> build.name!!
            build.isCompanion -> "<companion object>"
            else -> error("Unexpected type (anon. class?): $build")
        }
        if (build.typeVariables.isNotEmpty()) {
            name += build.typeVariables.joinToString(prefix = "<", postfix = ">") {
                it.name
            }
        }
        "Class(${name})"
    }

    override val modifiers get() = spec.modifiers.toList()

    override fun toString() = spec.tempBuild().toString()

    val isCompanion get() = spec.tempBuild().isCompanion
    val isInterface get() = spec.tempBuild().kind == TypeSpec.Kind.INTERFACE
    val isObject get() = spec.tempBuild().kind == TypeSpec.Kind.OBJECT

    override lateinit var packageName: String
        private set

    lateinit var type: CodegenType
        private set

    override fun onAddedToParent(parent: CodegenDeclaration<*>) {
        super.onAddedToParent(parent)
        packageName = parent.packageName
        type = when (parent) {
            is CodegenFile -> CodegenType.from(packageName + "." + spec.tempBuild().name!!)
            is CodegenClass -> when {
                isCompanion -> CodegenType.from(parent.type.name.canonicalName + ".Companion")
                else -> CodegenType.from(parent.type.name.canonicalName + "." + spec.tempBuild().name!!)
            }
            else -> error("CodegenClass added to invalid parent: $parent")
        }
    }
}

class CodegenProperty(spec: PropertySpec.Builder) : CodegenDeclaration<PropertySpec.Builder>(spec) {
    override val uid by lazy { "Property(${spec.build().name})" }

    override val modifiers get() = spec.modifiers

    override fun toString() = spec.build().toString()

    override lateinit var packageName: String
        private set

    override fun onAddedToParent(parent: CodegenDeclaration<*>) {
        super.onAddedToParent(parent)
        packageName = parent.packageName
    }
}