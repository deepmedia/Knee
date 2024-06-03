import java.util.concurrent.Callable
plugins {
    kotlin("jvm")
    `kotlin-dsl`
    `java-gradle-plugin`
    id("io.deepmedia.tools.deployer")
}

val injectProjectConfig by tasks.registering {
    val kneeVersion = providers.gradleProperty("knee.version")
    val kneeGroup = providers.gradleProperty("knee.group")
    val file = layout.buildDirectory.get().dir("generated").dir("sources").dir("config").file("KneeConfig.kt").asFile
    inputs.property("knee.config", Callable { kneeVersion.get() + kneeGroup.get() }) // Is this needed? Probably not
    outputs.dir(file.parentFile)
    doLast {
        file.delete()
        file.writeText("""
            // Generated file
            package io.deepmedia.tools.knee.plugin.gradle
            
            internal const val KneeVersion = "${kneeVersion.get()}"
            internal const val KneeGroup = "${kneeGroup.get()}"
        """.trimIndent())
    }
}

kotlin {
    jvmToolchain(11)
    sourceSets["main"].kotlin.srcDir(injectProjectConfig)
}

/* sourceSets {
    main {
        java.srcDir("$buildDir/generated/sources/config/")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(injectProjectConfig)
} */

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    compileOnly("com.android.tools.build:gradle:8.1.1")
}

gradlePlugin {
    isAutomatedPublishing = true
    plugins {
        create("knee") {
            id = "io.deepmedia.tools.knee"
            implementationClass = "io.deepmedia.tools.knee.plugin.gradle.KneePlugin"
        }
    }
}

deployer {
    content.gradlePluginComponents {
        kotlinSources()
        emptyDocs()
    }
}

// Gradle 7.X has embedded kotlin version 1.6, but kotlin-dsl plugins are compiled with 1.4 for compatibility with older
// gradle versions (I guess). 1.4 is very old and generates a warning, so let's bump to the embedded kotlin version.
// https://handstandsam.com/2022/04/13/using-the-kotlin-dsl-gradle-plugin-forces-kotlin-1-4-compatibility/
// https://github.com/gradle/gradle/blob/7a69f2f3d791044b946040cd43097ce57f430ca8/subprojects/kotlin-dsl-plugins/src/main/kotlin/org/gradle/kotlin/dsl/plugins/dsl/KotlinDslCompilerPlugins.kt#L48-L49
/* afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            val embedded = embeddedKotlinVersion.split(".").take(2).joinToString(".")
            apiVersion = embedded
            languageVersion = embedded
        }
    }
} */