plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    // IrisTest resolves Iris classes only when invoked, behind a mod-loaded guard.
    compileOnly(libs.iris)
}

loom {
    accessWidenerPath = file("src/main/resources/glue-gametest.accesswidener")
}
