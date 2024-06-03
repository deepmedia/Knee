enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        mavenLocal()
    }

    plugins {
        kotlin("multiplatform") version "2.0.0" apply false
        kotlin("plugin.serialization") version "2.0.0" apply false
        kotlin("jvm") version "2.0.0" apply false
        id("io.deepmedia.tools.deployer") version "0.11.0" apply false
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
    }
}

include(":knee-annotations")
include(":knee-runtime")
include(":knee-compiler-plugin")
include(":knee-gradle-plugin")
