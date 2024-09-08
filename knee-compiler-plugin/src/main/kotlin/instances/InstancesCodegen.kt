package io.deepmedia.tools.knee.plugin.compiler.instances

import com.squareup.kotlinpoet.*


object InstancesCodegen {
    // Note: this is also hardcoded in kneeUnwrapInstance for exception handling (knee-runtime)
    const val HandleField = "\$knee"

    /**
     * [preserveSymbols]: whether it should be allowed to act on the [HandleField] constructor
     * and/or field, for example through the JVM runtime functions `kneeWrapInstance` and `kneeUnwrapInstance`
     * (which use reflection) or simply via JNI (which doesn't respect access control modifiers anyway).
     *
     * Since we don't want to make them public, we use internal + [PublishedApi].
     */
    fun TypeSpec.Builder.addHandleConstructorAndField(
        preserveSymbols: Boolean,
    ) {
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addModifiers(KModifier.INTERNAL)
                .apply {
                    if (preserveSymbols) addAnnotation(PublishedApi::class)
                }
                .addParameter(HandleField, LONG)
                .build()
        )
        addProperty(
            PropertySpec.builder(HandleField, LONG)
                .addModifiers(KModifier.INTERNAL)
                .addAnnotation(JvmField::class)
                .apply {
                    if (preserveSymbols) addAnnotation(PublishedApi::class)
                }
                .initializer(HandleField)
                .build())
    }

    fun TypeSpec.Builder.addAnyOverrides(verbose: Boolean) {
        val pkg = "io.deepmedia.tools.knee.runtime.compiler"
        val type = this.build().name!!
        addFunction(FunSpec.builder("finalize")
            .let { if (verbose) it.addKdoc("knee:instances") else it }
            .addModifiers(KModifier.PROTECTED)
            .returns(UNIT)
            .addCode("%M(`$HandleField`)", MemberName(pkg, "kneeDisposeInstance"))
            .build())
        addFunction(FunSpec.builder("toString")
            .let { if (verbose) it.addKdoc("knee:instances") else it }
            .addModifiers(KModifier.OVERRIDE)
            .returns(STRING)
            .addCode("return %M(`$HandleField`)", MemberName(pkg, "kneeDescribeInstance"))
            .build())
        addFunction(FunSpec.builder("hashCode")
            .let { if (verbose) it.addKdoc("knee:instances") else it }
            .addModifiers(KModifier.OVERRIDE)
            .returns(INT)
            .addCode("return %M(`$HandleField`)", MemberName(pkg, "kneeHashInstance"))
            .build())
        addFunction(FunSpec.builder("equals")
            .let { if (verbose) it.addKdoc("knee:instances") else it }
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("other", ANY.copy(nullable = true))
            .returns(BOOLEAN)
            .addCode("return other is `$type` && %M(`$HandleField`, other.`$HandleField`)", MemberName(pkg, "kneeCompareInstance"))
            .build())
    }

}