plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

repositories { mavenCentral() }

dependencies {
    api("io.github.trethore:jcefgithub:146.0.10.1:all-relocated") { isTransitive = false }
    include("io.github.trethore:jcefgithub:146.0.10.1:all-relocated") { isTransitive = false }
    // The native archive installer needs the public AbstractStreamBuilder API absent in MC's 2.17.
    api("commons-io:commons-io:2.20.0")
    include("commons-io:commons-io:2.20.0")
}

tasks.matching { it.name.startsWith("publish") }.configureEach { enabled = false }

val pnpm = if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd", "/c", "pnpm") else listOf("pnpm")
val installWeb by tasks.registering(Exec::class) {
    inputs.files("web/package.json", "web/pnpm-lock.yaml", "web/pnpm-workspace.yaml")
    outputs.file("web/node_modules/.pnpm/lock.yaml")
    commandLine(pnpm + listOf("--dir", "web", "install", "--frozen-lockfile"))
}
val buildWeb by tasks.registering(Exec::class) {
    dependsOn(installWeb)
    inputs.files(fileTree("web/src"), "web/build.mjs", "web/pnpm-lock.yaml")
    outputs.dir(layout.buildDirectory.dir("generated/webResources"))
    commandLine(pnpm + listOf("--dir", "web", "build"))
}
sourceSets.main { resources.srcDir(buildWeb) }
