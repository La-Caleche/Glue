import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.ide.RunConfigSettings
import net.fabricmc.loom.task.RunGameTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import java.time.Duration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject

buildscript {
    // Applied Kotlin scripts need Loom's API; reuse the root's plugin classpath and version.
    dependencies { classpath(files(rootProject.buildscript.configurations.getByName("classpath"))) }
}

// javac generates this index without loading Minecraft. Fabric still owns test execution.
data class DiscoveredClientTest(val name: String, val binaryName: String, val explicitOnly: Boolean)

object ClientTestDiscovery {
    fun read(index: File): List<DiscoveredClientTest> {
        if (!index.isFile) throw GradleException("Missing client test index: enable glue-gametest on gametestAnnotationProcessor")
        val tests = index.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields = line.split('\t')
            if (fields.size != 3 || fields[2] !in listOf("true", "false")) {
                throw GradleException("Malformed client test index entry: $line")
            }
            DiscoveredClientTest(fields[0], fields[1], fields[2].toBoolean())
        }.sortedWith(compareBy({ it.name }, { it.binaryName }))
        if (tests.isEmpty()) throw GradleException("No concrete FabricClientGameTest implementation was discovered")
        return tests
    }

    fun select(tests: List<DiscoveredClientTest>, selectors: List<String>): List<DiscoveredClientTest> {
        val selected = linkedMapOf<String, DiscoveredClientTest>()
        for (selector in selectors.flatMap { it.split(',') }.map(String::trim)) {
            if (selector.isEmpty()) throw GradleException("Client test selectors must not be empty")
            val wildcard = '*' in selector
            val pattern = Regex(selector.split('*').joinToString(".*") { Regex.escape(it) })
            val matches = when (selector) {
                "auto" -> tests.filterNot { it.explicitOnly }
                "all" -> tests
                else -> tests.filter {
                    pattern.matches(it.name) || pattern.matches(it.binaryName) || pattern.matches(it.binaryName.substringAfterLast('.'))
                }
            }
            if (matches.isEmpty()) {
                throw GradleException("No client test matches '$selector'. Available: ${tests.joinToString { it.name }}. Use listClientTests for class names.")
            }
            if (!wildcard && selector !in listOf("auto", "all") && matches.size > 1) {
                throw GradleException("Ambiguous client test '$selector': ${matches.joinToString { it.binaryName }}. Select a fully qualified class name.")
            }
            matches.forEach { selected[it.binaryName] = it }
        }
        return selected.values.toList()
    }
}

/** Uses Loom's launcher, with one disposable profile and selected test-mod jar per task. */
abstract class RunClientTests @Inject constructor(settings: RunConfigSettings) : RunGameTask(settings) {
    @get:Input
    abstract val testPatterns: ListProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testIndex: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val descriptorTemplate: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testOutputs: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    @get:Internal
    abstract val assetsDirectory: DirectoryProperty

    @get:Internal
    abstract val profileDirectory: DirectoryProperty

    @get:Internal
    abstract val testModJar: RegularFileProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    init {
        testPatterns.convention(listOf("auto"))
        doNotTrackState("Prepares a disposable game profile and launches a live Minecraft client")
    }

    @Option(option = "tests", description = "Client test names, class names or * patterns; repeat or separate with commas (default: auto)")
    fun selectTests(selectors: List<String>) {
        testPatterns.set(selectors)
    }

    override fun exec() {
        if (testSources.isEmpty) throw GradleException("No Java client test sources found")
        val selected = ClientTestDiscovery.select(ClientTestDiscovery.read(testIndex.get().asFile), testPatterns.get())
        @Suppress("UNCHECKED_CAST")
        val descriptor = JsonSlurper().parse(descriptorTemplate.get().asFile) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val entrypoints = (descriptor["entrypoints"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        entrypoints["fabric-client-gametest"] = selected.map { it.binaryName }
        descriptor["entrypoints"] = entrypoints
        systemProperty("fabric.client.gametest.modid", descriptor["id"].toString())

        val profile = profileDirectory.get().asFile
        fileSystem.delete { delete(profile) }
        fileSystem.copy {
            from(assetsDirectory)
            into(profile)
            exclude("**/README.md", "**/.gitkeep")
        }

        // Do not rewrite shared compiled resources: different profile tasks can select different tests.
        val mod = testModJar.get().asFile
        mod.parentFile.mkdirs()
        JarOutputStream(mod.outputStream().buffered()).use { jar ->
            val names = mutableSetOf("fabric.mod.json")
            jar.putNextEntry(JarEntry("fabric.mod.json"))
            jar.write((JsonOutput.prettyPrint(JsonOutput.toJson(descriptor)) + "\n").toByteArray(Charsets.UTF_8))
            jar.closeEntry()
            for (root in testOutputs.files.filter(File::isDirectory).sortedBy(File::getPath)) {
                for (file in root.walkTopDown().filter(File::isFile).sortedBy { it.relativeTo(root).invariantSeparatorsPath }) {
                    val name = file.relativeTo(root).invariantSeparatorsPath
                    if (name == "fabric.mod.json") continue
                    if (!names.add(name)) throw GradleException("Duplicate test-mod resource: $name")
                    jar.putNextEntry(JarEntry(name))
                    file.inputStream().use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }
        logger.lifecycle("Selected client tests: ${selected.joinToString { it.name }}")
        logger.lifecycle("Test assets: ${assetsDirectory.get().asFile} -> $profile")
        super.exec()
    }
}

val sources = extensions.getByType<SourceSetContainer>()
val loom = extensions.getByType<LoomGradleExtensionAPI>()
val gametest = sources.getByName("gametest")
val compileClientTests = tasks.named<JavaCompile>(gametest.compileJavaTaskName)
val clientTestIndex = compileClientTests.flatMap { it.destinationDirectory.file("META-INF/glue/client-tests.tsv") }

tasks.register("listClientTests") {
    group = "verification"
    description = "Lists automatically discovered Fabric client tests without starting Minecraft"
    dependsOn(compileClientTests)
    doLast {
        if (gametest.allJava.isEmpty) throw GradleException("No Java client test sources found")
        ClientTestDiscovery.read(clientTestIndex.get().asFile).forEach {
            logger.lifecycle("${it.name}${if (it.explicitOnly) " [explicit]" else ""} -> ${it.binaryName}")
        }
    }
}

for (profile in listOf("", "sodium", "iris").filter { it.isEmpty() || sources.findByName(it) != null }) {
    val suffix = profile.replaceFirstChar(Char::uppercaseChar)
    val taskName = "clientTest$suffix"
    val runDirectory = layout.buildDirectory.dir("run/$taskName")
    val modJar = layout.buildDirectory.file("client-test-mods/$taskName.jar")
    val settings = objects.newInstance(RunConfigSettings::class.java, project, taskName).apply {
        inherit(loom.runs.getByName("client"))
        source(gametest)
        runDir(project.relativePath(runDirectory.get().asFile))
        property("fabric.client.gametest")
        property("fabric.client.gametest.testModResourcesPath", gametest.resources.srcDirs.first().absolutePath)
    }
    tasks.register(taskName, RunClientTests::class.java, settings).configure {
        group = "verification"
        description = "Runs Fabric client tests (${profile.ifEmpty { "vanilla" }}); use --tests to select scenarios"
        dependsOn("configureClientLaunch", gametest.classesTaskName)
        testIndex.set(clientTestIndex)
        descriptorTemplate.fileProvider(tasks.named<ProcessResources>(gametest.processResourcesTaskName).map { it.destinationDir.resolve("fabric.mod.json") })
        testOutputs.from(gametest.output)
        testSources.from(gametest.allJava)
        assetsDirectory.set(layout.projectDirectory.dir("src/test/assets"))
        profileDirectory.set(runDirectory)
        testModJar.set(modJar)
        // The isolated jar owns the test mod instead of the shared source-set class/resource roots.
        setClasspath(gametest.runtimeClasspath - gametest.output + files(modJar))
        if (profile.isNotEmpty()) classpath(sources.getByName(profile).runtimeClasspath)
        timeout.set(Duration.ofMinutes(10))
    }
}
