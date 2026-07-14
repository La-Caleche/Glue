plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

repositories {
    maven("https://api.modrinth.com/maven")
}

dependencies {
    implementation(project.project(":glue-core").sourceSets.getByName("main").output)
    implementation(project.project(":glue-render").sourceSets.getByName("main").output)
    implementation(project.project(":glue-lumos").sourceSets.getByName("main").output)

    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    compileOnly(libs.iris)

    implementation("org.lwjgl:lwjgl-nfd:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos-arm64")
}

loom {
    runs {
        named("client") {
            client()
            configName = "Glue Showcase"
            ideConfigGenerated(true)
            runDir("../run")

            if (providers.gradleProperty("lc.fabric.username").orNull == null ||
                providers.gradleProperty("lc.fabric.uuid").orNull == null ||
                providers.gradleProperty("lc.fabric.accesstoken").orNull == null
            )
                return@named

            programArg("--username")
            programArg(providers.gradleProperty("lc.fabric.username").get())
            programArg("--uuid")
            programArg(providers.gradleProperty("lc.fabric.uuid").get())
            programArg("--accessToken")
            programArg(providers.gradleProperty("lc.fabric.accesstoken").get())
            programArg("--userType")
            programArg("mojang")
        }
    }

    accessWidenerPath = project.project(":glue-render").file("src/main/resources/glue-render.accesswidener")

}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    remapJar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(rootDir.resolve("build").resolve("libs"))
    }
}

tasks.configureEach {
    group = null
}
