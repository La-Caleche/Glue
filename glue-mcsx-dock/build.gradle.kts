plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    api(project(path = ":glue-mcsx", configuration = "namedElements"))
}
