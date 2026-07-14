plugins {
    id("fabric-loom") apply false
    id("fr.lacaleche.caldle") apply false
}

subprojects {
    apply(plugin = "fabric-loom")
    apply(plugin = "fr.lacaleche.caldle")

    repositories {
        maven("https://api.modrinth.com/maven")
    }
}

tasks.matching { it.name == "publish" }.configureEach {
    enabled = false
}
