plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

repositories {
    maven("https://maven.izzel.io/releases/")
}

dependencies {
    implementation(project(path = ":glue-core", configuration = "namedElements"))

    /** ModernUI/Arc3D bundle their own copies of libraries Minecraft already provides. */
    fun icyllis(configuration: String, dependency: Provider<MinimalExternalModuleDependency>) =
        configuration(dependency) {
            exclude(group = "it.unimi.dsi", module = "fastutil")
            exclude(group = "com.google.code.findbugs", module = "jsr305")
        }
    // api: views, canvases, and fragments in MCSX's public surface are ModernUI types.
    icyllis("api", libs.modernui.core)
    icyllis("implementation", libs.arc3d.core)
    icyllis("implementation", libs.arc3d.sketch)
    icyllis("implementation", libs.arc3d.engine)
    icyllis("implementation", libs.arc3d.granite)
    icyllis("implementation", libs.arc3d.opengl)
    icyllis("implementation", libs.arc3d.vulkan)
    icyllis("implementation", libs.arc3d.compiler)

    api(libs.taffy)

    modApi(libs.mui.lite)
}
