plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    // Optional at runtime: the built-in iris-shaders tool resolves Iris classes only when a test
    // invokes it, behind a mod-loaded guard.
    compileOnly(libs.iris)
}
