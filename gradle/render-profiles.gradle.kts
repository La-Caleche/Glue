import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.SourceSetContainer

buildscript {
    // Applied Kotlin scripts need Loom's API; reuse the root's plugin classpath and version.
    dependencies { classpath(files(rootProject.buildscript.configurations.getByName("classpath"))) }
}

val loom = extensions.getByType<LoomGradleExtensionAPI>()
val sources = extensions.getByType<SourceSetContainer>()
val libraries = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
val main = sources.getByName("main")

// Empty runtime source sets let Loom remap optional mods without adding them to the base client.
val sodium = sources.create("sodium") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    runtimeClasspath += main.output + main.runtimeClasspath
}
val iris = sources.create("iris") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    runtimeClasspath += sodium.runtimeClasspath
}
loom.createRemapConfigurations(sodium)
loom.createRemapConfigurations(iris)

dependencies {
    add("modSodiumRuntimeOnly", libraries.findLibrary("sodium").get())
    add("modIrisRuntimeOnly", libraries.findLibrary("iris").get())
    add("irisRuntimeOnly", "org.anarres:jcpp:1.4.14")
    add("irisRuntimeOnly", "io.github.douira:glsl-transformer:3.0.0-pre3")
    add("irisRuntimeOnly", "org.antlr:antlr4-runtime:4.13.1")
}

for (profile in listOf(sodium, iris)) {
    val suffix = profile.name.replaceFirstChar(Char::uppercaseChar)
    loom.runs.create("client$suffix") {
        inherit(loom.runs.getByName("client"))
        source(profile)
        runDir(loom.runs.getByName("client").runDir)
        configName = "Glue Showcase ($suffix)"
    }
}
