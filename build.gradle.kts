import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

// Everything identical across the library and its development modules lives here; build files
// declare only what is genuinely project-specific (extra dependencies, access wideners, run configs).
allprojects {
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

// The library itself: `src/main` runs on both sides, `src/client` only on the client.
loom {
    splitEnvironmentSourceSets()

    mods {
        register("glue") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }

    accessWidenerPath = file("src/main/resources/glue.accesswidener")

    // The showcase is the runnable project.
    runs {
        remove(getByName("client"))
        remove(getByName("server"))
    }
}

val client = sourceSets.getByName("client")

dependencies {
    "clientCompileOnly"(libs.iris)

    // Native OS file dialogs. Declared on main so the published POM, the jar's single dependency
    // list, carries them.
    implementation(libs.lwjgl.nfd)
    val nfdVersion = libs.versions.lwjgl.nfd.get()
    listOf("windows", "linux", "macos", "macos-arm64").forEach { platform ->
        runtimeOnly("org.lwjgl:lwjgl-nfd:$nfdVersion:natives-$platform")
    }
}

// Client tests exercise rendering, viewport and file-dialog code without starting Minecraft.
sourceSets.test {
    compileClasspath += client.output + client.compileClasspath
    runtimeClasspath += client.output + client.runtimeClasspath
}

// The published artifacts: the library and the client test helpers.
tasks.register("libraryJars") {
    group = "build"
    description = "Builds the remapped jar of every published project into build/libs/"
    dependsOn(":remapJar", ":glue-gametest:remapJar")
}
