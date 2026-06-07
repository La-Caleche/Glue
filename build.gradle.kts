plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

repositories {
    maven("https://api.modrinth.com/maven")
}

val testmod: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

loom {
    runs {
        register("testmodClient") {
            client()
            name("Testmod Client")
            source(testmod)
        }
    }
    createRemapConfigurations(testmod)
    accessWidenerPath = file("src/main/resources/glue.accesswidener")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    compileOnly(libs.iris)

    // LWJGL Native File Dialog - for OS file open/save dialogs
    implementation("org.lwjgl:lwjgl-nfd:3.3.3")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-nfd:3.3.3:natives-macos-arm64")

    "testmodImplementation"(sourceSets.main.get().output)

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
        archiveBaseName.set("${rootProject.name}-${project.name}")
        destinationDirectory.set(rootDir.resolve("build").resolve("libs"))
    }
}
