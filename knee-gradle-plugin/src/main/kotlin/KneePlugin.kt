package io.deepmedia.tools.knee.plugin.gradle

import com.android.build.api.dsl.CommonExtension
import io.deepmedia.tools.knee.plugin.gradle.utils.*
import io.deepmedia.tools.knee.plugin.gradle.utils.androidAbi
import io.deepmedia.tools.knee.plugin.gradle.utils.configureValidSourceSets
import io.deepmedia.tools.knee.plugin.gradle.utils.isValidBackend
import io.deepmedia.tools.knee.plugin.gradle.utils.isValidFrontend
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.KonanTarget

@Suppress("unused")
class KneePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        @Suppress("ConstPropertyName")
        const val Version = KneeVersion
    }

    override fun apply(target: Project) {
        val knee = target.extensions.create("knee", KneeExtension::class.java)
        knee.projectName = target.name

        applyDependencies(target, knee)
        applyConnection(target, knee)
    }

    private fun applyDependencies(target: Project, knee: KneeExtension) {
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.kotlinExtension as KotlinMultiplatformExtension
            kotlin.configureValidSourceSets(isValid = { isValidBackend || isValidFrontend }, log = knee::log) { sourceSet ->
                knee.log("Adding knee-runtime and knee-annotations dependency to $sourceSet...")
                sourceSet.dependencies {
                    implementation("$KneeGroup:knee-runtime:$KneeVersion")
                    implementation("$KneeGroup:knee-annotations:$KneeVersion")
                }
            }
        }
        target.plugins.withId("org.jetbrains.kotlin.android") {
            knee.log("Adding knee-runtime and knee-annotations dependency to main sourceSet...")
            target.kotlinExtension.sourceSets["main"].dependencies {
                implementation("$KneeGroup:knee-runtime:$KneeVersion")
                implementation("$KneeGroup:knee-annotations:$KneeVersion")
            }
        }
    }

    private fun applyConnection(target: Project, knee: KneeExtension) {
        val connectionInfo = run {
            val binaryDirectory = target.layout.buildDirectory.get().dir("knee").dir("bin")
            mapOf(
                NativeBuildType.DEBUG to binaryDirectory.dir("debug"),
                NativeBuildType.RELEASE to binaryDirectory.dir("release"),
            )
        }
        connectionInfo.forEach { (build, directory) ->
            prepareConnection(target, knee, build, directory)
        }
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.kotlinExtension as KotlinMultiplatformExtension
            target.afterEvaluate {
                if (knee.connectTargets.get()) {
                    val backends = kotlin.targets.matching { it.isValidBackend }.toList()
                    val frontends = kotlin.targets.matching { it.isValidFrontend }.toList()
                    if (backends.isEmpty() || frontends.isEmpty()) return@afterEvaluate
                    connectionInfo.forEach { (build, directory) ->
                        @Suppress("UNCHECKED_CAST")
                        performConnection(target, knee, build, directory, backends as List<KotlinNativeTarget>)
                    }
                }
            }
        }
    }

    /**
     * Starting from Kotlin 2.0.0/AGP-something, jniLibs.srcDir() doesn't seem to work if called in after evaluate.
     * So we need to run this in the configuration stage, even if [KneeExtension.connectTargets] was/will be set to false!
     */
    private fun prepareConnection(
        target: Project,
        knee: KneeExtension,
        buildType: NativeBuildType,
        directory: Directory
    ) {
        val androidBuildType = buildType.toString().lowercase()
        target.configureAndroidExtension {
            it.sourceSets {
                getByName(androidBuildType) {
                    knee.log("[prepareConnection] [$buildType] android.sourceSets.$androidBuildType.jniLibsSrc = $directory")
                    jniLibs.srcDir(directory)
                }
            }
        }
    }

    @Suppress("DefaultLocale")
    private fun performConnection(
        target: Project,
        knee: KneeExtension,
        buildType: NativeBuildType,
        binaryDirectory: Directory,
        backends: List<KotlinNativeTarget>
    ) {
        knee.log("[performConnection] [$buildType] backends=${backends.map { it.name }}")
        // 1. add debug and release shared libraries for all backend targets
        val binaries = backends.map { backend ->
            val backendBinaryDirectory = binaryDirectory.dir(backend.androidAbi)
            val lib = with(backend.binaries) {
                knee.log("[performConnection/1] [$buildType] [backend:${backend.name}] creating sharedLib")
                findSharedLib(buildType) ?: run {
                    sharedLib(listOf(buildType))
                    getSharedLib(buildType)
                }
            }
            // 2. generate shared libraries in a folder that AGP likes
            // lib.outputDirectory is a var but it sometimes fails to change the linkTask which is what matters
            // depends on order of instantiations. Do both, just in case someone depends on lib.outputDirectory
            lib.outputDirectory = backendBinaryDirectory.asFile
            knee.log("[performConnection/2] [$buildType] [backend:${backend.name}] configured sharedLib ${lib.name} (${lib.outputDirectory})")
            lib.linkTaskProvider.configure { destinationDirectory.set(backendBinaryDirectory) }
            lib
        }

        // 3. link AGP tasks
        // One option is the merge<Debug/Release>JniLibFolders, but that only models one of the dependencies (.so files)
        // and not the other (.kt files), so we resort to pre<Debug/Release>Build.
        // TODO: check if there's a way to add the dependency implicitly in jniLibs.srcDir and java.srcDir
        // TODO: if there isn't, model dependencies separately. There's no reason why something like compileDebugKotlinAndroid
        //  should LINK the binaries. It should only compile the source code.
        val androidBuildType = buildType.toString().lowercase()
        target.tasks.named("pre${androidBuildType.capitalize()}Build").configure {
            knee.log("[performConnection/3] [$buildType] task $name now depends on binary tasks ${binaries.map { it.linkTaskName }}")
            dependsOn(*binaries.map { it.linkTaskName }.toTypedArray())
        }

        // 4. pass the bin folder and the source code folder to AGP
        target.configureAndroidExtension {
            it.sourceSets {
                named("main").configure {
                    // We generate source code for all backends, but we can only use one - let's take the first
                    // Note: we do this twice, one per build type. Should prob do it once.
                    // Note: intentionally using 'java', 'kotlin' would not work for some reason.
                    knee.log("[performConnection/4] [$buildType] android.sourceSets.main.javaSrc = ${knee.generatedSourceDirectory(backends.first()).get()}")
                    java.srcDir(knee.generatedSourceDirectory(backends.first()).get())
                }
            }
        }
    }

    override fun getCompilerPluginId() = "knee-compiler-plugin"

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = KneeGroup,
        artifactId = getCompilerPluginId(),
        version = KneeVersion
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.isValidBackend
                && kotlinCompilation.compilationName == KotlinCompilation.MAIN_COMPILATION_NAME
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val knee = project.extensions.getByType(KneeExtension::class.java)

        // Compute the output directory using the target name
        // We can ignore compilation name because of isApplicable - we only apply on 'main'
        val outputDir = knee.generatedSourceDirectory(kotlinCompilation.target as KotlinNativeTarget)
        kotlinCompilation.compileTaskProvider.get().outputs.dir(outputDir)
        return project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = knee.enabled.get().toString()),
                SubpluginOption(key = "verboseLogs", value = knee.verboseLogs.get().toString()),
                SubpluginOption(key = "verboseRuntime", value = knee.verboseRuntime.get().toString()),
                SubpluginOption(key = "verboseSources", value = knee.verboseSources.get().toString()),
                SubpluginOption(key = "outputDir", value = outputDir.get().asFile.absolutePath),
            )
        }
    }
}
