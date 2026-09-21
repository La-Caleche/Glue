import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

// One file for a player instead of four. The modules keep their own jars, their own ids and their
// own environment: Fabric loads each nested mod as if it had been installed on its own, so a
// dedicated server still skips the client half of Lumos.
//
// glue-web stays out: 3.6 MB of Chromium installer against 550 KB for everything here, and nothing
// in this suite depends on it.
dependencies {
    include(project(":glue-core"))
    include(project(":glue-render"))
    include(project(":glue-lumos"))
    include(project(":glue-lumos-client"))
}

tasks.processResources {
    from(project(":glue-core").sourceSets.main.get().resources) {
        include("assets/glue/icon.png")
    }
}

// build/dist, not build/libs: the root project is also named glue, and its own jar lands at
// build/libs/glue-<version>.jar. Sharing that path let whichever task ran last decide which jar a player got.
tasks.named<RemapJarTask>("remapJar") {
    archiveBaseName.set("glue")
    destinationDirectory.set(rootDir.resolve("build").resolve("dist"))
}

// A distribution artifact, not a library: nothing should depend on it from Maven.
tasks.matching { it.name == "publish" || it.name == "publishToMavenLocal" }.configureEach {
    enabled = false
}
