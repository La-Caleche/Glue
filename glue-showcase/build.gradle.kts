plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project(path = ":glue-core", configuration = "namedElements"))
    implementation(project(path = ":glue-render", configuration = "namedElements"))
    implementation(project(path = ":glue-lumos", configuration = "namedElements"))
    implementation(project(path = ":glue-lumos-client", configuration = "namedElements"))
    implementation(project(path = ":glue-mcsx", configuration = "namedElements"))

    compileOnly(libs.iris)

    val irisRuntime: Boolean = providers.gradleProperty("glue.showcase.iris").orNull == "true"
    val sodiumRuntime: Boolean = providers.gradleProperty("glue.showcase.sodium").orNull == "true"

    if (sodiumRuntime || irisRuntime) {
        modRuntimeOnly(libs.sodium)
    }

    if (irisRuntime) {
        modRuntimeOnly(libs.iris)
        runtimeOnly("org.anarres:jcpp:1.4.14")
        runtimeOnly("io.github.douira:glsl-transformer:3.0.0-pre3")
        runtimeOnly("org.antlr:antlr4-runtime:4.13.1")
    }

    implementation(libs.lwjgl.nfd)
    val nfdVersion = libs.versions.lwjgl.nfd.get()
    listOf("windows", "linux", "macos", "macos-arm64").forEach { platform ->
        runtimeOnly("org.lwjgl:lwjgl-nfd:$nfdVersion:natives-$platform")
    }
}

loom {
    runs {
        configureEach {
            vmArg("-Dfabric.log.disableAnsi=false")
        }

        named("client") {
            client()
            configName = "Glue Showcase"
            ideConfigGenerated(true)
            runDir("../run")
        }

        named("server") {
            server()
            configName = "Glue Showcase Server"
            ideConfigGenerated(true)
            runDir("../run-server")
        }
    }

    accessWidenerPath = project.project(":glue-render").file("src/main/resources/glue-render.accesswidener")
}

tasks.configureEach {
    group = null
}
