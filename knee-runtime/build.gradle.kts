import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("io.deepmedia.tools.deployer")
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("backend") {
                // withNative()
                group("androidNative") {
                    withAndroidNative()
                }
                group("prebuiltHeaders") {
                    withMacos()
                    withMingwX64()
                    withLinuxX64()
                }
            }
        }
    }

    // https://github.com/androidx/androidx/blob/0d0dcddc46e8267a2f7546f40fc6c2fbb1516c3d/buildSrc/private/src/main/kotlin/androidx/build/clang/NativeTargetCompilation.kt#L91
    // https://github.com/jonnyzzz/kotlin-jni-mix/blob/94ca9a01efec003d35fea96a3de87c517b88e5be/build.gradle.kts#L37
    // https://github.com/mpetuska/fake-kamera/commit/d5a2e5bcef4c754fc3f6b231e9ea318e826ce56a
    // https://github.com/DatL4g/Sekret/blob/f1f3d30bf5b1ffed1b518a908cdd8515b078cdc3/sekret-lib/build.gradle.kts#L41
    fun KotlinNativeTarget.configurePrebuiltCinterop(folder: String, subfolder: String) {
        val file = rootProject.layout.projectDirectory.dir("dependencies").dir("jdk17").dir(folder).dir("include")
        val defFiles = layout.projectDirectory.dir("src").dir("prebuiltHeadersMain").dir("interop")
        compilations[KotlinCompilation.MAIN_COMPILATION_NAME].cinterops {
            create("jni") {
                definitionFile.set(defFiles.file("jni_prebuilt.def"))
                packageName("io.deepmedia.tools.knee.runtime.internal")
                includeDirs(file, file.dir(subfolder))
            }
        }
    }


    // backend
    androidNativeArm32() // { configureAndroidCInterop() }
    androidNativeArm64() // { configureAndroidCInterop() }
    androidNativeX64() //  { configureAndroidCInterop() }
    androidNativeX86() // { configureAndroidCInterop() }
    // linuxX64 { configurePrebuiltCinterop("linux-x86", "linux") }
    // mingwX64 { configurePrebuiltCinterop("windows-x86", "win32") }
    // macosArm64 { configurePrebuiltCinterop("darwin-arm64", "darwin") }
    // macosX64 { configurePrebuiltCinterop("darwin-x86", "darwin") }

    // frontend
    jvmToolchain(11)
    jvm(name = "frontend")


    sourceSets.configureEach {
        languageSettings {
            optIn("kotlin.ExperimentalUnsignedTypes")
            optIn("kotlinx.cinterop.UnsafeNumber")
            optIn("kotlinx.cinterop.ExperimentalForeignApi")
            optIn("kotlin.experimental.ExperimentalNativeApi")
        }
    }

    sourceSets.commonMain.configure {
        dependencies {
            api(project(":knee-annotations"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
    }
}

deployer {
    content.kotlinComponents {
        emptyDocs()
    }
}

// Solves a problem with includeBuild() in the tests module
val runCommonizer by tasks.registering { }
