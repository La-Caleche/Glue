plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project.project(":glue-core").sourceSets.getByName("main").output)

    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    compileOnly(libs.iris)
    // Sodium is never a runtime requirement: the adapter is behind runtime detection and
    // falls back when absent. compileOnly only so the mixins can be typed instead of @Pseudo.
    modCompileOnly(libs.sodium)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    accessWidenerPath = file("src/main/resources/glue-render.accesswidener")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    remapJar {
        archiveBaseName.set(project.name)
        destinationDirectory.set(rootDir.resolve("build").resolve("libs"))
    }
}
