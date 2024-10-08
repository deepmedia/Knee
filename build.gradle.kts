import io.deepmedia.tools.deployer.DeployerExtension
import io.deepmedia.tools.deployer.specs.SonatypeAuth

plugins {
    kotlin("multiplatform") apply false
    kotlin("jvm") apply false
    kotlin("plugin.serialization") apply false
    id("io.deepmedia.tools.deployer") apply false
}

subprojects {
    group = providers.gradleProperty("knee.group").get()
    version = providers.gradleProperty("knee.version").get()

    // Publishing
    plugins.withId("io.deepmedia.tools.deployer") {
        extensions.configure<DeployerExtension> {
            verbose.set(providers.environmentVariable("CI").orElse("").map { it.isNotBlank() })

            projectInfo {
                description.set("A Kotlin Compiler Plugin for seamless communication between Kotlin/Native and Kotlin/JVM.")
                url.set("https://github.com/deepmedia/Knee")
                scm.fromGithub("deepmedia", "Knee")
                license(apache2)
                developer("natario1", "mattia@deepmedia.io", "DeepMedia", "https://deepmedia.io")
            }

            signing {
                key.set(secret("SIGNING_KEY"))
                password.set(secret("SIGNING_PASSWORD"))
            }

            // use "deployLocal" to deploy to local maven repository
            localSpec {
                // directory = rootProject.layout.buildDirectory.dir("inspect")
                signing.password = absent()
                signing.key = absent()
            }

            // use "deploySonatype" to deploy to OSSRH / maven central
            nexusSpec {
                syncToMavenCentral = true
                auth.user.set(secret("SONATYPE_USER"))
                auth.password.set(secret("SONATYPE_PASSWORD"))
            }

            // use "deploySonatypeSnapshot" to deploy to sonatype snapshots repo
            nexusSpec("snapshot") {
                auth.user.set(secret("SONATYPE_USER"))
                auth.password.set(secret("SONATYPE_PASSWORD"))
                repositoryUrl.set(ossrhSnapshots1)
                release.version.set("latest-SNAPSHOT")
            }

            // use "deployGithub" to deploy to github packages
            githubSpec {
                repository.set("Knee")
                owner.set("deepmedia")
                auth {
                    user.set(secret("GHUB_USER"))
                    token.set(secret("GHUB_PERSONAL_ACCESS_TOKEN"))
                }
            }
        }
    }
}
