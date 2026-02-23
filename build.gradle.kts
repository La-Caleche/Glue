plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

repositories {
}

loom {
    accessWidenerPath = file("src/main/resources/glue.accesswidener")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    remapJar {
        archiveBaseName.set("${rootProject.name}-${project.name}")
        destinationDirectory.set(rootDir.resolve("build").resolve("libs"))
    }
}