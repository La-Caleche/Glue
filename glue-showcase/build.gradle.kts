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

            // CLI verification runs: -Pglue.showcase.quickplay=<world> boots straight into a
            // singleplayer world. -Pglue.showcase.runReal=true retargets to the REAL ../run
            // profile (gradle resolves runDir against this project, hence the extra "../"; the
            // generated IDE config resolves the plain "../run" against the workspace root and is
            // untouched) -- only possible while no IDE session holds its file locks. Optionally
            // -Pglue.showcase.autoshot=<seconds> saves rotating screenshots for reading back and
            // -Pglue.showcase.autotest=true spawns demo lights around the player on join.
            providers.gradleProperty("glue.showcase.quickplay").orNull?.let { world ->
                programArg("--quickPlaySingleplayer")
                programArg(world)
            }
            if (providers.gradleProperty("glue.showcase.runReal").orNull == "true") {
                runDir("../../run")
            }
            providers.gradleProperty("glue.showcase.autoshot").orNull?.let { seconds ->
                vmArg("-Dglue.showcase.autoshot=$seconds")
            }
            providers.gradleProperty("glue.lumos.irisStage").orNull?.let { stage ->
                vmArg("-Dglue.lumos.irisStage=$stage")
            }
            if (providers.gradleProperty("glue.showcase.autotest").orNull == "true") {
                vmArg("-Dglue.showcase.autotest=true")
            }
            // -Pglue.showcase.gametest=<name> runs a scripted GameTest (see testmod.gametest) and
            // closes the game when it finishes; add gametestKeepOpen=true to stay in the session.
            providers.gradleProperty("glue.showcase.gametest").orNull?.let { test ->
                vmArg("-Dglue.showcase.gametest=$test")
            }
            if (providers.gradleProperty("glue.showcase.gametestKeepOpen").orNull == "true") {
                vmArg("-Dglue.showcase.gametest.keepOpen=true")
            }
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
