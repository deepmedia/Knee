package io.deepmedia.tools.knee.plugin.gradle

import tasks.UnpackageCodegenSources
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName


/**
 * Provides support for multimodule codegen dependencies.
 * - [registerPackageTask] must be called on producer modules
 * - [registerUnpackageTask] must be called on the consumer module
 * The package task will create a JAR with the JVM generated sources. The unpackage task will fetch and unzip such
 * sources so that they can be added as a source set.
 *
 * The code here is a bit complex due to having to support two use cases:
 * 1. remote repositories. In this case the package codegen task output should be added
 *    as a publishing artifact, e.g. packagename-1.0.0-codegen.jar so we can fetch it in frontend.
 * 2. project(":dep") dependencies. In this case we must take care to add the [NativeTargetAttribute]
 *    and create outgoing configurations in the backend, which Gradle treats like variant in a published module.
 *
 * NOTE: a project should only register the package task if it does not have the JVM/Android targets.
 * If it has Android targets, the codegen data can be included with [KneeExtension.connectTargets].
 */
object KneePackaging {

    private const val Classifier = "knee-codegen"
    private const val JarArtifactType = "knee-codegen-zipped"
    private const val DirArtifactType = "knee-codegen-unzipped"

    // It's not strictly necessary, but let's reuse common attribute names like artifactType and "org.jetbrains.kotlin.native.target"
    private val ArtifactTypeAttribute = Attribute.of("artifactType", String::class.java)
    private val NativeTargetAttribute = Attribute.of("org.jetbrains.kotlin.native.target", String::class.java)

    private const val ConfigurationNamePrefix = "knee"
    private const val ConfigurationNameSuffix = "Codegen"

    fun registerPackageTask(
        target: KotlinNativeTarget
    ): TaskProvider<Jar> {
        val project = target.project
        val targetName = target.name.replaceFirstChar(Char::titlecase)
        val konanName = target.konanTarget.name
        val taskName = "packageKnee${targetName}Codegen"

        return runCatching { project.tasks.named(taskName, Jar::class.java) }.getOrNull() ?: project.tasks.register<Jar>(taskName) {
            // ${archiveBaseName}-${archiveAppendix}-${archiveVersion}-${archiveClassifier}.${archiveExtension}
            val knee = project.extensions.getByType<KneeExtension>()
            from(knee.generatedSourceDirectory(target))
            dependsOn(target.compilations[KotlinCompilation.MAIN_COMPILATION_NAME].compileKotlinTaskName)
            archiveClassifier.set(Classifier)
            archiveAppendix.set(target.name.lowercase())
        }.also { task ->
            // stuff below is to support project(":dep")
            val outgoing = project.configurations.create("$ConfigurationNamePrefix$targetName$ConfigurationNameSuffix") {
                isCanBeResolved = false
                isCanBeConsumed = true
                attributes {
                    attribute(ArtifactTypeAttribute, JarArtifactType)
                    attribute(NativeTargetAttribute, konanName)
                }
            }
            project.artifacts {
                add(outgoing.name, task) { builtBy(task) }
            }
        }
    }

    fun createUnpackageConfiguration(
        project: Project,
        architecture: KonanTarget
    ): Configuration {
        // Note: it should be possible to define this as unzipped DirArtifactType, but there's a gradle bug:
        // https://github.com/gradle/gradle/issues/8386 so we're forced to use artifactView {} below instead.
        val targetName = architecture.presetName.replaceFirstChar(Char::titlecase)
        val konanName = architecture.name
        val configuration = project.configurations.create("$ConfigurationNamePrefix$targetName$ConfigurationNameSuffix") {
            isTransitive = false
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(ArtifactTypeAttribute, JarArtifactType)
                attribute(NativeTargetAttribute, konanName)
            }
        }

        // Add a 'codegen' variant to every dependency, pointing at dep-1.0.0-`Classifier`.jar.
        // Some of them won't have this jar, in which case adding it to the codegen configuration would throw.
        // https://docs.gradle.org/current/userguide/component_metadata_rules.html#making_variants_published_as_classified_jars_explicit
        project.dependencies {
            components.all {
                val details = this
                // The variant name should not matter.
                addVariant("knee-codegen") {
                    attributes {
                        attribute(ArtifactTypeAttribute, JarArtifactType)
                        attribute(NativeTargetAttribute, konanName)
                    }
                    withFiles {
                        removeAllFiles()
                        addFile("${details.id.name}-${details.id.version}-$Classifier.jar")
                    }
                }
            }

            // Add the ability to unzip the incoming jars.
            registerTransform(UnzipTransform::class.java) {
                from.attribute(ArtifactTypeAttribute, JarArtifactType)
                to.attribute(ArtifactTypeAttribute, DirArtifactType)
            }
        }

        return configuration
    }

    fun registerUnpackageTask(
        project: Project,
        configuration: Configuration
    ): TaskProvider<UnpackageCodegenSources> {
        check(configuration.name.startsWith(ConfigurationNamePrefix)) { "Invalid configuration: ${configuration.name}" }
        check(configuration.name.endsWith(ConfigurationNameSuffix)) { "Invalid configuration: ${configuration.name}" }
        val targetName = configuration.name.removeSurrounding(ConfigurationNamePrefix, ConfigurationNameSuffix)
        return project.tasks.register<UnpackageCodegenSources>("unpackageKnee${targetName}Codegen") {
            codegenFiles.from(configuration.incoming.artifactView {
                attributes.attribute(ArtifactTypeAttribute, DirArtifactType)
            }.files)
        }
    }

}