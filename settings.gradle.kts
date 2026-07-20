pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.fabricmc.net/")

        maven {
            name = "La Calèche Private"
            url = uri("https://reposilite.lacaleche.cc/private")
            credentials {
                username = System.getenv("REPOSILITE_TOKEN_NAME")
                    ?: providers.gradleProperty("lc.reposilite.readonly.name").orNull
                            ?: throw GradleException("No REPOSILITE_TOKEN_NAME environment variable or lc.reposilite.readonly.name property found")
                password = System.getenv("REPOSILITE_TOKEN_SECRET")
                    ?: providers.gradleProperty("lc.reposilite.readonly.token").orNull
                            ?: throw GradleException("No REPOSILITE_TOKEN_SECRET environment variable or lc.reposilite.readonly.token property found")
            }
        }
    }

    plugins {
        id("fabric-loom") version settings.providers.gradleProperty("loomVersion")
        id("fr.lacaleche.caldle") version settings.providers.gradleProperty("caldleVersion")
        id("fr.lacaleche.caldle.dev-dependencies-override") version settings.providers.gradleProperty("caldleVersion")
    }
}

plugins {
    id("fr.lacaleche.caldle.dev-dependencies-override")
}

rootProject.name = "glue"

val modules = listOf("core", "render", "lumos", "lumos-client", "showcase")

modules.forEach { include("glue-$it") }
