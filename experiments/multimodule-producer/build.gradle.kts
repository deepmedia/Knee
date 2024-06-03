import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("io.deepmedia.tools.knee") version "0.2.0-SNAPSHOT"
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

android {
    namespace = "io.deepmedia.tools.knee.sample.mm.producer"
    compileSdk = 33
    defaultConfig {
        minSdk = 26
        targetSdk = 33
    }
    sourceSets {
        configureEach {
            kotlin.srcDir("src/android${name.capitalize()}/kotlin")
            manifest.srcFile("src/android${name.capitalize()}/AndroidManifest.xml")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

knee {
    enabled.set(true)
    verbose.set(true)
    autoBind.set(true)
}

kotlin {
    jvmToolchain(11)
    // frontend
    androidTarget()

    // backend
    val configureBackendTarget: KotlinNativeTarget.() -> Unit = {
        fun KotlinCompilation<*>.configureBackendSourceSet() {
            val sets = kotlin.sourceSets
            val parent = sets.maybeCreate("backend${name.capitalize()}")
            parent.dependsOn(sets["common${name.capitalize()}"])
            defaultSourceSet.dependsOn(parent)
        }
        compilations[KotlinCompilation.MAIN_COMPILATION_NAME].configureBackendSourceSet()
        compilations[KotlinCompilation.TEST_COMPILATION_NAME].configureBackendSourceSet()
    }
    androidNativeArm32(configure = configureBackendTarget)
    androidNativeArm64(configure = configureBackendTarget)
    androidNativeX64(configure = configureBackendTarget)
    androidNativeX86(configure = configureBackendTarget)
}

val c by tasks.registering {
    dependsOn("compileKotlinAndroidNativeArm32")
}

val l by tasks.registering {
    dependsOn("linkDebugSharedAndroidNativeArm32")
}

/**
 * This is to make included parent build work. Kotlin Compiler Plugins have a configuration
 * issue: https://youtrack.jetbrains.com/issue/KT-53477/ . We workaround this in kotlin-compiler-plugin
 * by using a fat JAR, but this fat JAR is exported from the "shadow" configuration, while included builds
 * read from the "default" configuration and there doesn't seem to be a clean way to solve this.
 */
configurations.matching {
    it.name.startsWith("kotlin") && it.name.contains("CompilerPluginClasspath")
}.all {
    isTransitive = true
}