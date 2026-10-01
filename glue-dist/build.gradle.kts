import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    include(project(":glue-core"))
    include(project(":glue-render"))
}

tasks.processResources {
    from(project(":glue-core").sourceSets.main.get().resources) {
        include("assets/glue/icon.png")
    }
}

tasks.named<RemapJarTask>("remapJar") {
    archiveBaseName.set("glue")
    destinationDirectory.set(rootDir.resolve("build").resolve("dist"))
}

tasks.matching { it.name == "publish" || it.name == "publishToMavenLocal" }.configureEach {
    enabled = false
}
