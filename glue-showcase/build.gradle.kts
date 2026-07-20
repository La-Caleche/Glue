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

    // Iris hard-depends on Sodium 0.7.x, so enabling it has to pull Sodium in even when the Sodium flag is off.
    if (providers.gradleProperty("glue.showcase.sodium").orNull != "false" || irisRuntime) {
        modRuntimeOnly(libs.sodium)
    }

    if (irisRuntime) {
        modRuntimeOnly(libs.iris)
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
            // Loom's dev log4j config strips ANSI colors unless told otherwise; this survives
            // run-config regeneration, unlike a flag added by hand in the IDE.
            vmArg("-Dfabric.log.disableAnsi=false")
        }

        named("client") {
            client()
            configName = "Glue Showcase"
            ideConfigGenerated(true)
            runDir("../run")

            if (providers.gradleProperty("lc.fabric.username").orNull == null ||
                providers.gradleProperty("lc.fabric.uuid").orNull == null ||
                providers.gradleProperty("lc.fabric.accesstoken").orNull == null
            )
                return@named

            programArg("--username")
            programArg(providers.gradleProperty("lc.fabric.username").get())
            programArg("--uuid")
            programArg(providers.gradleProperty("lc.fabric.uuid").get())
            programArg("--accessToken")
            programArg(providers.gradleProperty("lc.fabric.accesstoken").get())
            programArg("--userType")
            programArg("mojang")
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
