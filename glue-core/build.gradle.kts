plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    implementation("org.lwjgl:lwjgl-nfd:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos-arm64")

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
