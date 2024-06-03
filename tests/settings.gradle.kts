pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        mavenLocal()
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

include("test-coroutines")
include("test-interfaces")
include("test-primitives")
include("test-imports")
include("test-classes")
include("test-misc")

rootProject.name = "KneeTests"