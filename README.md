[![Build Status](https://github.com/deepmedia/Knee/actions/workflows/build.yml/badge.svg?event=push)](https://github.com/deepmedia/Knee/actions)
[![Release](https://img.shields.io/github/release/deepmedia/Knee.svg)](https://github.com/deepmedia/Knee/releases)
[![Issues](https://img.shields.io/github/issues-raw/deepmedia/Knee.svg)](https://github.com/deepmedia/Knee/issues)

![Project logo](assets/logo_256.png)

# 🦵 Knee 🦵

A Kotlin compiler plugin and companion runtime tools that provides seamless communication between Kotlin/Native 
binaries and Kotlin/JVM, using a thin and efficient layer around the JNI interface.

With Knee, you can write idiomatic Kotlin/Native code, annotate it and then invoke it transparently from JVM 
as if they were running on the same environment.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    id("io.deepmedia.tools.knee") version "1.1.1"
}
```

Please check out [the documentation](https://opensource.deepmedia.io/knee).
