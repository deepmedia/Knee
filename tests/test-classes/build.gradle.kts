@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("multiplatform") version "2.0.20"
    id("com.android.application") version "8.1.1"
    id("io.deepmedia.tools.knee")
}

configurations.configureEach {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

android {
    namespace = "io.deepmedia.tools.knee.tests"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

knee {

}

kotlin {
    jvmToolchain(11)

    applyDefaultHierarchyTemplate {
        common {
            group("backend") {
                withAndroidNative()
            }
        }
    }

    // frontend
    androidTarget()

    // backend
    androidNativeArm64()
    androidNativeX64()
    androidNativeArm32()
    androidNativeX86()
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