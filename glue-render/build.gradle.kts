plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    api(project(path = ":glue-core", configuration = "namedElements"))

    compileOnly(libs.iris)
    // Sodium is never a runtime requirement: the adapter is behind runtime detection and
    // falls back when absent. compileOnly only so the mixins can be typed instead of @Pseudo.
    modCompileOnly(libs.sodium)

    // Native OS file dialogs (moved here from glue-core with the client file-dialog API).
    implementation(libs.lwjgl.nfd)
    val nfdVersion = libs.versions.lwjgl.nfd.get()
    listOf("windows", "linux", "macos", "macos-arm64").forEach { platform ->
        runtimeOnly("org.lwjgl:lwjgl-nfd:$nfdVersion:natives-$platform")
    }
}

loom {
    accessWidenerPath = file("src/main/resources/glue-render.accesswidener")
}
