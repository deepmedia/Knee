pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        mavenLocal()
    }
    plugins {
        kotlin("multiplatform") version "1.9.23" apply false
        kotlin("jvm") version "1.9.23" apply false
        id("com.android.application") version "8.1.1" apply false
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
    }
}

includeBuild("..")

include("compose-notes")
include("expect-actual")
include("multimodule-producer")
include("multimodule-consumer")

rootProject.name = "KneeSamples"