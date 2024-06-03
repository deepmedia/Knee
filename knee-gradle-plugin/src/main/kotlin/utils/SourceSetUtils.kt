package io.deepmedia.tools.knee.plugin.gradle.utils

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

// "afterEvaluate" does nothing when the project is already in executed state
/* private fun <T> Project.whenEvaluated(fn: Project.() -> T) {
    if (state.executed) fn()
    else afterEvaluate { fn() }
} */

internal val KotlinTarget.isValidBackend: Boolean
    get() = platformType == KotlinPlatformType.native
            && (this as KotlinNativeTarget).konanTarget.family == Family.ANDROID

internal val KotlinTarget.isValidFrontend: Boolean
    get() = platformType == KotlinPlatformType.androidJvm

/**
 * Adding the runtime dependency to multiplatform projects it's tricky because
 * 1. We don't really know the target hierarchy
 * 2. Kotlin plugin is not smart enough to commonize the dependency in some edge cases
 * For example, if:
 * - we add the dep individually to all four androidNative* targets
 * - project has all androidNative* targets plus ios targets
 * ... then in the project androidNativeMain, Knee won't resolve.
 *
 * I think in the long run this will be fixed by Kotlin, but for now we use afterEvaluate
 * and the same logic that AtomicFU uses:
 * https://github.com/Kotlin/kotlinx-atomicfu/blob/0.22.0/atomicfu-gradle-plugin/src/main/kotlin/kotlinx/atomicfu/plugin/gradle/AtomicFUGradlePlugin.kt#L375
 *
 * Still there are a couple of issues
 * 1. It takes a snapshot of the targets in afterEvaluate. Other targets might be added later
 * 2. It is redundant. We'll add the dependency to all source sets that share valid targets, like
 *    androidNativeX86Main, androidNativeX86Test, androidNativeMain, androidNativeTest, ...
 */
internal fun KotlinMultiplatformExtension.configureValidSourceSets(
    isValid: KotlinTarget.() -> Boolean,
    log: (String) -> Unit,
    block: (KotlinSourceSet) -> Unit
) {
    targets.configureEach {
        if (isValid()) {
            log("configureValidSourceSets: $targetName is valid, accepting all compilations...")
            compilations.configureEach { defaultSourceSet(block) }
        } else if (platformType != KotlinPlatformType.common) {
            log("configureValidSourceSets: $targetName is invalid, dropping all compilations...")
        } else {
            // Intermediate source sets are added as compilations to the metadata/common target.
            // Investigate them, assuming that by the time the compilation is created, all targets are already known
            // This might not be true
            compilations.configureEach {
                log("configureValidSourceSets: investigating common:$compilationName...")
                val associatedTargets = this@configureValidSourceSets.targets.matching {
                    if (it.platformType == KotlinPlatformType.common) return@matching false
                    val descendantSets = it.compilations.flatMap { it.allKotlinSourceSets }
                    descendantSets.intersect(kotlinSourceSets).isNotEmpty()
                }.toList()
                if (associatedTargets.isNotEmpty() && associatedTargets.all(isValid)) {
                    log("configureValidSourceSets: common:$compilationName accepted ($associatedTargets)")
                    defaultSourceSet(block)
                } else {
                    log("configureValidSourceSets: common:$compilationName rejected ($associatedTargets)")
                }
            }
        }
    }

    // Old impl
    /* this.project.whenEvaluated {
        val sourceSets = hashMapOf<KotlinSourceSet, MutableList<KotlinCompilation<*>>>()
        targets.flatMap { it.compilations }.forEach { compilation ->
            compilation.allKotlinSourceSets.forEach { sourceSet ->
                sourceSets.getOrPut(sourceSet) { mutableListOf() }.add(compilation)
            }
            log("configureValidSourceSets: Analyzing ${compilation.target.targetName}::${compilation::compilationName}: sets = ${compilation.allKotlinSourceSets.map { it.name }}")
        }
        val matches = sourceSets.filter { (set, compilations) ->
            val valid = compilations
                .filter { it.platformType != KotlinPlatformType.common }
                .all { it.target.isValid() }
            if (valid) {
                log("configureValidSourceSets: ${set.name} ACCEPTED (all non-common compilations belong to a valid target)")
            } else {
                log("configureValidSourceSets: ${set.name} REJECTED (some non-common compilations belong to invalid targets)")
            }
            valid
        }
        matches.forEach {
            block(it.key)
        }
    } */
}