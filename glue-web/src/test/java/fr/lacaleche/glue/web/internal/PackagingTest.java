package fr.lacaleche.glue.web.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackagingTest {

    @Test
    void bundlesNativeAbiBridgeAndPrivateInstallerDependenciesWithoutDemoOrGameClasses() throws IOException {
        try (ZipFile jar = new ZipFile(System.getProperty("glue.web.bundledJar"))) {
            assertNotNull(jar.getEntry("org/cef/CefApp.class"));
            assertNotNull(jar.getEntry("fr/lacaleche/glue/web/internal/shaded/jcefgithub/CefAppBuilder.class"));
            assertNull(jar.getEntry("io/github/trethore/jcefgithub/CefAppBuilder.class"));
            assertNotNull(jar.getEntry("fr/lacaleche/glue/web/WebSurface.class"));
            assertNotNull(jar.getEntry("fr/lacaleche/glue/web/internal/shaded/commonsio/build/AbstractStreamBuilder.class"));
            assertNotNull(jar.getEntry("assets/glue-web/shaders/core/web.fsh"));
            assertNotNull(jar.getEntry("glue-web.client.mixins.json"));
            assertNotNull(jar.getEntry("fr/lacaleche/glue/web/internal/mixin/GuiOverlayMixin.class"));
            assertNotNull(jar.getEntry("assets/glue-web/web/bridge.js"));
            assertNotNull(jar.getEntry("assets/glue-web/lang/fr_fr.json"));
            assertNotNull(jar.getEntry("assets/glue-web/lang/en_us.json"));
            assertNull(jar.getEntry("org/apache/commons/io/build/AbstractStreamBuilder.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("net/minecraft/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("fr/lacaleche/glue/testmod/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().endsWith("/index.html") || entry.getName().endsWith("/app.js")));
            assertNotNull(jar.getEntry("META-INF/licenses/jcefgithub/LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/licenses/commons-io/META-INF/LICENSE.txt"));
        }
    }
}
