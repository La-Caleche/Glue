package fr.lacaleche.glue.gametest.internal.discovery;

import fr.lacaleche.glue.gametest.ClientTestSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTestProcessorTest {

    @TempDir
    Path output;

    @Test
    void discoversUnannotatedInheritedAndStaticNestedClassesWithoutInitializingThem() throws Exception {
        Compilation result = this.compile(
                source("example.Base", "public abstract class Base implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}"),
                source("example.InventoryClientTest", """
                        public class InventoryClientTest extends Base {
                            static final Object NEVER_INITIALIZE = fail();
                            private static Object fail() { throw new AssertionError("Discovery must not initialize a test"); }
                        }
                        """),
                source("example.RenderTests", """
                        public class RenderTests {
                            public abstract static class Fixture extends Base {}
                            @fr.lacaleche.glue.gametest.ClientTestSpec(value="shader-hud", explicitOnly=true)
                            public static class HUD extends Fixture {}
                            public static class HTTPMenuClientTest extends Fixture {}
                            public static class Helper {}
                        }
                        """));
        assertTrue(result.success(), result.diagnostics());
        assertEquals(List.of(
                "inventory\texample.InventoryClientTest\tfalse",
                "http-menu\texample.RenderTests$HTTPMenuClientTest\tfalse",
                "shader-hud\texample.RenderTests$HUD\ttrue"), this.index());
    }

    @Test
    void rewritesTheIndexWhenATestIsRemovedRenamedOrBecomesAbstract() throws Exception {
        String contract = "implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest";
        assertTrue(this.compile(source("example.OldClientTest", "public class OldClientTest " + contract + " {}"),
                source("example.RemainingClientTest", "public class RemainingClientTest " + contract + " {}")).success());

        Compilation renamed = this.compile(source("example.NewClientTest", "public class NewClientTest " + contract + " {}"),
                source("example.RemainingClientTest", "public abstract class RemainingClientTest " + contract + " {}"));
        assertTrue(renamed.success(), renamed.diagnostics());
        assertEquals(List.of("new\texample.NewClientTest\tfalse"), this.index());

        assertTrue(this.compile(source("example.Helper", "public class Helper {}")).success());
        assertTrue(this.index().isEmpty(), "An empty compilation result must not retain the previous index");
    }

    @Test
    void followsCompiledSuperclassesWithoutRediscoveringDependencyTests() throws Exception {
        assertTrue(this.compile(source("dependency.ParentClientTest",
                "public class ParentClientTest implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}")).success());
        Compilation result = this.compile(source("example.ChildClientTest", "public class ChildClientTest extends dependency.ParentClientTest {}"));
        assertTrue(result.success(), result.diagnostics());
        assertEquals(List.of("child\texample.ChildClientTest\tfalse"), this.index());
    }

    @Test
    void rejectsTestsFabricCannotInstantiate() throws Exception {
        Compilation result = this.compile(source("example.Broken", """
                public class Broken {
                    public class NonStatic implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}
                    static class Hidden implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}
                    public static class WithArgument implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {
                        public WithArgument(int argument) {}
                    }
                }
                """));
        assertFalse(result.success());
        assertTrue(result.diagnostics().contains("must be static"), result.diagnostics());
        assertTrue(result.diagnostics().contains("must be public"), result.diagnostics());
        assertTrue(result.diagnostics().contains("no-argument constructor"), result.diagnostics());
    }

    @Test
    void rejectsMetadataOnFixturesAndReservedOrMalformedNames() throws Exception {
        Compilation result = this.compile(source("example.Broken", """
                public class Broken {
                    @fr.lacaleche.glue.gametest.ClientTestSpec
                    public abstract static class Fixture implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}
                    @fr.lacaleche.glue.gametest.ClientTestSpec("auto")
                    public static class Reserved extends Fixture {}
                    @fr.lacaleche.glue.gametest.ClientTestSpec("bad,name")
                    public static class Malformed extends Fixture {}
                }
                """));
        assertFalse(result.success());
        assertTrue(result.diagnostics().contains("requires a concrete"), result.diagnostics());
        assertTrue(result.diagnostics().contains("Invalid client test name 'auto'"), result.diagnostics());
        assertTrue(result.diagnostics().contains("Invalid client test name 'bad,name'"), result.diagnostics());
    }

    @Test
    void retainsDistinctClassesWithTheSameShortNameForQualifiedSelection() throws Exception {
        String body = "public class SameClientTest implements net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest {}";
        Compilation result = this.compile(source("one.SameClientTest", body), source("two.SameClientTest", body));
        assertTrue(result.success(), result.diagnostics());
        assertEquals(List.of("same\tone.SameClientTest\tfalse", "same\ttwo.SameClientTest\tfalse"), this.index());
    }

    @Test
    void processorIsRegisteredForJavacServiceDiscovery() {
        assertTrue(ServiceLoader.load(Processor.class).stream()
                .anyMatch(provider -> provider.type().equals(ClientTestProcessor.class)));
    }

    private Compilation compile(Source... sources) throws IOException, URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Discovery tests require a JDK");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path classes = Files.createDirectories(this.output.resolve("classes"));
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            files.setLocationFromPaths(StandardLocation.CLASS_PATH,
                    List.of(Path.of(ClientTestSpec.class.getProtectionDomain().getCodeSource().getLocation().toURI()), classes));
            List<Source> inputs = new ArrayList<>();
            // A nominal interface is sufficient to exercise javac inheritance; no Minecraft classes are loaded.
            inputs.add(source("net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest", "public interface FabricClientGameTest {}"));
            inputs.addAll(List.of(sources));
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, List.of("--release", "21"), null, inputs);
            task.setProcessors(List.of(new ClientTestProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics().toString());
        }
    }

    private List<String> index() throws IOException {
        return Files.readAllLines(this.output.resolve("classes").resolve(ClientTestProcessor.INDEX), StandardCharsets.UTF_8)
                .stream().filter(line -> !line.startsWith("#")).toList();
    }

    private static Source source(String name, String body) {
        return new Source(name, "package " + name.substring(0, name.lastIndexOf('.')) + ";\n" + body);
    }

    private record Compilation(boolean success, String diagnostics) {
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String content;

        private Source(String name, String content) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return this.content;
        }
    }
}
