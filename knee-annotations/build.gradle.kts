import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    `maven-publish`
    id("io.deepmedia.tools.deployer")
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("backend") {
                withNative()
            }
        }
    }

    // native targets
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()
    // linuxX64()
    // mingwX64()
    // macosArm64()
    // macosX64()

    // for other consumers
    jvmToolchain(11)
    jvm(name = "frontend")
}

deployer {
    content.kotlinComponents {
        emptyDocs()
    }
}

