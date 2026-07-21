import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd

plugins {
    id("fabric-loom")
    id("fr.lacaleche.caldle")
}

/**
 * The vendored ModernUI host keeps its upstream conventions and copyright headers. Authored
 * Mixins intentionally use the Minecraft convention of `$`-separated collision-resistant names,
 * which the generic Java MethodName rule cannot represent.
 */
val lintExcludes: Set<String> = listOf("mui", "client/mixin")
    .map { "**/fr/lacaleche/glue/mcsx/$it/**" }
    .toSet()

tasks.withType<Checkstyle>().configureEach { setExcludes(lintExcludes) }
tasks.withType<Pmd>().configureEach { setExcludes(lintExcludes) }

repositories {
    maven("https://maven.izzel.io/releases/")
}

val iconFont: Configuration by configurations.creating

dependencies {
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

    iconFont(libs.font.awesome)
}

loom {
    accessWidenerPath = file("src/main/resources/mcsx.accesswidener")
}

tasks.processResources {
    from({ iconFont.map { zipTree(it) } }) {
        include("META-INF/resources/webjars/font-awesome/6.5.2/webfonts/fa-solid-900.ttf")
        eachFile { path = "assets/mcsx/fonts/icons.ttf" }
        includeEmptyDirs = false
    }
    from({ iconFont.map { zipTree(it) } }) {
        include("META-INF/resources/webjars/font-awesome/6.5.2/LICENSE.txt")
        eachFile { path = "META-INF/licenses/mcsx/font-awesome.txt" }
        includeEmptyDirs = false
    }
}
