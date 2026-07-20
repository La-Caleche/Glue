plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project(path = ":glue-core", configuration = "namedElements"))
    // api: Light/Lumos (glue-lumos) and EmissiveMaterial (glue-render) appear in this module's public API.
    api(project(path = ":glue-lumos", configuration = "namedElements"))
    api(project(path = ":glue-render", configuration = "namedElements"))
}
