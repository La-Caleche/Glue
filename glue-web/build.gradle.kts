import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
    id("com.gradleup.shadow")
}

repositories { mavenCentral() }

val embedded by configurations.creating
configurations.compileOnly { extendsFrom(embedded) }

dependencies {
    embedded("io.github.trethore:jcefgithub:146.0.10.1:all-relocated") { isTransitive = false }
    embedded("commons-io:commons-io:2.20.0") { isTransitive = false }
}

shadow { addShadowVariantIntoJavaComponent = false }

val bundledJar = tasks.named<ShadowJar>("shadowJar") {
    // Caldle disables Shadow in Fabric mode by default; this jar is Loom's explicit remap input.
    enabled = true
    archiveBaseName.set(project.name)
    archiveClassifier.set("dev-shadow")
    configurations = listOf(embedded)
    // org.cef names are part of the native JNI ABI; the installer and its helpers remain private.
    relocate("io.github.trethore.jcefgithub", "fr.lacaleche.glue.web.internal.shaded.jcefgithub")
    relocate("org.apache.commons.io", "fr.lacaleche.glue.web.internal.shaded.commonsio")
    mergeServiceFiles()
    for (artifact in listOf("jcefgithub", "commons-io")) {
        from(provider { embedded.files.filter { it.name.startsWith("$artifact-") }.map { zipTree(it) } }) {
            include("LICENSE.txt", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
            into("META-INF/licenses/$artifact")
        }
    }
}

tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(bundledJar.flatMap { it.archiveFile })
}

// Development consumers exercise the same shaded implementation as the published remapped jar.
configurations.named("namedElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(bundledJar)
}
dependencies { testRuntimeOnly(files(bundledJar)) }

tasks.test {
    inputs.file(bundledJar.flatMap { it.archiveFile })
    doFirst { systemProperty("glue.web.bundledJar", bundledJar.get().archiveFile.get().asFile.absolutePath) }
}
