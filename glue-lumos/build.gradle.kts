plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project.project(":glue-core").sourceSets.getByName("main").output)
    implementation(project.project(":glue-render").sourceSets.getByName("main").output)

    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
