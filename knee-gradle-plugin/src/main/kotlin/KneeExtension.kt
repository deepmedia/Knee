package io.deepmedia.tools.knee.plugin.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import javax.inject.Inject

abstract class KneeExtension @Inject constructor(objects: ObjectFactory, private val layout: ProjectLayout, private val providers: ProviderFactory) {

    private fun Property<Boolean>.conventions(key: String, fallback: Boolean): Property<Boolean> {
        val env = providers.environmentVariable("io.deepmedia.knee.$key").map { it.toBoolean() }
        val prop = providers.gradleProperty("io.deepmedia.knee.$key").map { it.toBoolean() }
        return convention(prop.orElse(env).orElse(fallback))
    }

    val enabled: Property<Boolean> = objects.property<Boolean>().convention(true)

    val verboseLogs: Property<Boolean> = objects.property<Boolean>().conventions("verboseLogs", false)

    val verboseRuntime: Property<Boolean> = objects.property<Boolean>().conventions("verboseRuntime", false)

    val verboseSources: Property<Boolean> = objects.property<Boolean>().conventions("verboseSources", false)

    val connectTargets: Property<Boolean> = objects.property<Boolean>().conventions("connectTargets", true)

    /* val autoBind: Property<Boolean> = objects
        .property<Boolean>()
        .convention(false) */

    val generatedSourceDirectory: DirectoryProperty = objects
        .directoryProperty()
        .convention(layout.buildDirectory.map { it.dir("knee").dir("src") })

    fun generatedSourceDirectory(target: KotlinNativeTarget): Provider<Directory> {
        // note: this is possible because we only apply on the main compilation
        // otherwise we'd have to add a main/test subdirectory/suffix for example.
        return generatedSourceDirectory.dir(target.name)
    }

    internal fun log(message: String) {
        if (verboseLogs.get()) println("[KneePlugin] [${projectName}] $message")
    }

    internal lateinit var projectName: String
}