plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project(path = ":", configuration = "namedElements"))

    compileOnly(libs.iris)

    implementation(libs.lwjgl.nfd)
    val nfdVersion = libs.versions.lwjgl.nfd.get()
    listOf("windows", "linux", "macos", "macos-arm64").forEach { platform ->
        runtimeOnly("org.lwjgl:lwjgl-nfd:$nfdVersion:natives-$platform")
    }
}

loom {
    runs {
        named("client") {
            client()
            configName = "Glue Showcase"
            ideConfigGenerated(true)
            runDir("../../.run/client")
        }

        named("server") {
            server()
            configName = "Glue Showcase Server"
            ideConfigGenerated(true)
            runDir("../../.run/server")
        }
    }
}

fabricApi {
    // Writes src/main/generated, which joins the resources; the block shapes come from here.
    configureDataGeneration {
        modId.set("glue-showcase")
    }

    configureTests {
        createSourceSet.set(true)
        modId.set("glue-showcase-tests")
        enableGameTests.set(false)
        // The shared convention supplies per-profile tasks with native --tests options.
        enableClientGameTests.set(false)
    }
}

sourceSets.named("gametest") {
    java.setSrcDirs(listOf("src/test/e2e/java"))
    resources.setSrcDirs(listOf("src/test/e2e/resources"))
}

dependencies {
    "gametestImplementation"(project(path = ":glue-gametest", configuration = "namedElements"))
    "gametestAnnotationProcessor"(project(path = ":glue-gametest", configuration = "namedElements"))
}

apply(from = rootProject.file("gradle/render-profiles.gradle.kts"))
apply(from = rootProject.file("gradle/client-tests.gradle.kts"))
