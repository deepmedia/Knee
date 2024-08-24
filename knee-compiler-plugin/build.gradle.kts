plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.deepmedia.tools.deployer")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation("com.squareup:kotlinpoet:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

kotlin {
    target {
        compilerOptions {
            optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        }
    }
}

// Annoying configuration needed because of https://youtrack.jetbrains.com/issue/KT-53477/
// Compiler plugins can't have dependency in Native, unless we use a fat jar.
tasks.shadowJar.configure {
    // Remove the -all suffix, otherwise the plugin jar is not picked up
    // (very important. it won't throw an error either, just won't apply)
    archiveClassifier.set("")
    // But also change the destination directory (normally: build/libs), otherwise our output jar
    // will overwrite the `jar` task output (which has no classifier), and when two tasks have the same
    // outputs, Gradle can go crazy. Example:
    //
    // Task ':knee-compiler-plugin:signArtifacts0ForLocalPublication' uses this output of task
    // ':knee-compiler-plugin:jar' without declaring an explicit or implicit dependency.
    // This can lead to incorrect results being produced, depending on what order the tasks are executed.
    destinationDirectory.set(layout.buildDirectory.get().dir("libs").dir("shadow"))
}

deployer {
    content {
        component {
            fromArtifactSet {
                artifact(tasks.shadowJar)
            }
            kotlinSources()
            emptyDocs()
        }
    }
}
