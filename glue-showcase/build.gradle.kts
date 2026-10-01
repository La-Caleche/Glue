plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

dependencies {
    implementation(project(path = ":glue-core", configuration = "namedElements"))
    implementation(project(path = ":glue-render", configuration = "namedElements"))
    implementation(project(path = ":glue-lumos", configuration = "namedElements"))
    implementation(project(path = ":glue-lumos-client", configuration = "namedElements"))
    implementation(project(path = ":glue-web", configuration = "namedElements"))

    compileOnly(libs.iris)

    implementation(libs.lwjgl.nfd)
    val nfdVersion = libs.versions.lwjgl.nfd.get()
    listOf("windows", "linux", "macos", "macos-arm64").forEach { platform ->
        runtimeOnly("org.lwjgl:lwjgl-nfd:$nfdVersion:natives-$platform")
    }
}

// Frontend tooling belongs to the showcase. No library task depends on these tasks.
val webResources = layout.buildDirectory.dir("generated/webResources")
val pnpm = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "pnpm") else listOf("pnpm")
val installWeb by tasks.registering(Exec::class) {
    workingDir("web")
    inputs.files("web/package.json", "web/pnpm-lock.yaml")
    outputs.file("web/node_modules/.pnpm/lock.yaml")
    commandLine(pnpm + listOf("install", "--frozen-lockfile"))
}
val buildWeb by tasks.registering(Exec::class) {
    dependsOn(installWeb)
    workingDir("web")
    inputs.files(fileTree("web") {
        include("*.html", "*.js", "package.json", "pnpm-lock.yaml", "src/**", "public/**")
    })
    outputs.dir(webResources)
    commandLine(pnpm + "build")
}
sourceSets.main { resources.srcDir(buildWeb) }

loom {
    runs {
        named("client") {
            client()
            configName = "Glue Showcase"
            ideConfigGenerated(true)
            runDir("../../.run/client")
            // pnpm --dir glue-showcase/web watch rebuilds this directory; F5 reloads a page in game.
            vmArg("-Dglue.web.source.glue-showcase=${webResources.get().dir("assets/glue-showcase/web").asFile.absolutePath}")
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
