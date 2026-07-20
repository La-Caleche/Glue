import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom") apply false
    id("fr.lacaleche.caldle") apply false
}

// Everything identical across modules lives here; module build files declare only what is
// genuinely module-specific (extra dependencies, access wideners, run configs).
subprojects {
    apply(plugin = "fabric-loom")
    apply(plugin = "fr.lacaleche.caldle")

    repositories {
        maven("https://api.modrinth.com/maven")
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    val loom = extensions.getByType<LoomGradleExtensionAPI>()

    dependencies {
        "minecraft"(libs.findLibrary("minecraft").get())
        "mappings"(loom.officialMojangMappings())
        "modImplementation"(libs.findLibrary("fabric-loader").get())
        "modImplementation"(libs.findLibrary("fabric-api").get())
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named<ProcessResources>("processResources") {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    tasks.withType<RemapJarTask>().configureEach {
        archiveBaseName.set(project.name)
        destinationDirectory.set(rootDir.resolve("build").resolve("libs"))
    }
}

tasks.matching { it.name == "publish" }.configureEach {
    enabled = false
}

// The publishable modules — everything but the showcase demo mod.
val libraryModules = listOf("glue-core", "glue-render", "glue-lumos", "glue-lumos-client")

tasks.register("libraryJars") {
    group = "build"
    description = "Builds the remapped jar of every library module into build/libs/"
    libraryModules.forEach { dependsOn(":$it:remapJar") }
}
