package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.lint.McsxLinter;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs {@link McsxLinter} over everything that ships: each component file, and each demo screen
 * against the controller it is actually bound to. This is the build-time stand-in for {@code view.*}
 * having no tests — a screen naming a handler its controller lacks fails here, not in-game.
 *
 * <p>Documents are read from their source path rather than the classpath on purpose: a fixture in
 * {@code src/test/resources} shadows the real screen of the same name, which once made this suite
 * silently lint the wrong file.
 */
class ScreenLintTest {

    private static final Path COMPONENT_DIR = Path.of("src/main/resources/assets/mcsx/ui/components");
    private static final Path SCREEN_DIR = Path.of("src/main/resources/assets/mcsx/ui");
    // The library's own resources first, then this showcase's demo content.
    private static final List<Path> RESOURCE_ROOTS = List.of(
            Path.of("../glue-mcsx/src/main/resources"), Path.of("src/main/resources"));

    /** The consumer natives the demo registers — {@code <surface>}, mirroring {@code McsxDemos}. */
    private static final Set<String> NATIVE_TAGS = Set.of("surface");

    /** The controller each demo screen binds against, mirroring {@code McsxDemos}. */
    private static final Map<String, Class<? extends ScreenController>> CONTROLLERS =
            Map.of("glass", GlassController.class,
                    "editor", EditorController.class,
                    "theme_stress", ThemeStressController.class);

    @Test
    void everyShippedComponentLintsClean() {
        List<Path> files = filesIn(COMPONENT_DIR);
        assertFalse(files.isEmpty(), "no components under " + COMPONENT_DIR);
        for (Path file : files) {
            // A component has no controller: its refs are the caller's props.
            assertNoProblems(file, McsxLinter.lint(parse(file), null, NATIVE_TAGS));
        }
    }

    @Test
    void everyDockPaneLintsAgainstItsController() {
        List<Path> files = filesIn(SCREEN_DIR.resolve("dock"));
        assertFalse(files.isEmpty(), "no dock panes under " + SCREEN_DIR.resolve("dock"));
        for (Path file : files) {
            assertNoProblems(file, McsxLinter.lint(parse(file),
                    DockDemoController.class, NATIVE_TAGS));
        }
    }

    /** The F12 debug dockspace panes: markup errors here would only surface when the dock opens. */
    @Test
    void debugPanesLintAgainstTheirControllers() {
        Path debugDir = Path.of("src/main/resources/assets/mcsx/ui/debug");
        Map<String, Class<?>> controllers = Map.of(
                "lights", fr.lacaleche.glue.testmod.render.LightsPaneController.class,
                "properties", fr.lacaleche.glue.testmod.render.LightsPaneController.class,
                "effects", fr.lacaleche.glue.testmod.render.PostEffectsPaneController.class);
        List<Path> files = filesIn(debugDir);
        assertFalse(files.isEmpty(), "no debug panes under " + debugDir);
        for (Path file : files) {
            Class<?> controller = controllers.get(baseName(file));
            assertTrue(controller != null, "no controller mapped for " + file);
            assertNoProblems(file, McsxLinter.lint(parse(file), controller, Set.of()));
        }
    }

    @Test
    void everyDemoScreenLintsAgainstItsController() {
        List<Path> files = filesIn(SCREEN_DIR);
        assertFalse(files.isEmpty(), "no screens under " + SCREEN_DIR);
        for (Path file : files) {
            String name = baseName(file);
            Class<?> controller = CONTROLLERS.getOrDefault(name, DemoController.class);
            assertNoProblems(file, McsxLinter.lint(parse(file), controller, NATIVE_TAGS));
        }
    }

    @Test
    void everyDocumentImportResolves() {
        List<Path> documents = new ArrayList<>();
        for (Path root : RESOURCE_ROOTS) {
            try (Stream<Path> files = Files.walk(root)) {
                documents.addAll(files.filter(path -> path.toString().endsWith(".mcsx")).toList());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot scan " + root, e);
            }
        }

        Set<String> missing = new HashSet<>();
        for (Path document : documents) {
            for (String source : parse(document).imports().values()) {
                if (resolveImport(source) == null) {
                    missing.add(document + " -> " + source);
                }
            }
        }
        assertTrue(missing.isEmpty(), "unresolved imports: " + missing);
    }

    /** The linter must catch the failures that reached the game: a bad class and a missing handler. */
    @Test
    void catchesAMissingHandlerAndAnInvalidClass() {
        List<String> problems = McsxLinter.lint(
                McsxParser.parseDocument("<div class=\"flex-row floaty-thing\" onClick={noSuchHandler}/>"),
                DemoController.class, NATIVE_TAGS);
        assertTrue(problems.stream().anyMatch(p -> p.contains("noSuchHandler")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("floaty-thing")), problems.toString());
    }

    /** A tag that is neither a base tag, an import, nor a native component is a build error. */
    @Test
    void catchesADeletedComponentTag() {
        List<String> problems = McsxLinter.lint(
                McsxParser.parseDocument("<div><checkbox checked={enabled}/></div>"),
                DemoController.class, NATIVE_TAGS);
        assertTrue(problems.stream().anyMatch(p -> p.contains("<checkbox>")), problems.toString());
    }

    /**
     * {@code <option>} was listed as a base tag while no builder ever handled it, so a screen using
     * it linted clean and failed in-game — the inverse of the {@code <checkbox>} case above.
     */
    @Test
    void catchesATagNoBuilderHandles() {
        List<String> problems = McsxLinter.lint(
                McsxParser.parseDocument("<div><option value=\"a\"/></div>"),
                DemoController.class, NATIVE_TAGS);
        assertTrue(problems.stream().anyMatch(p -> p.contains("<option>")), problems.toString());
    }

    @Test
    void componentPropsMayForwardControllerHandlers() {
        McsxDocument document = McsxParser.parseDocument("""
                <import name="ThemeSwitch" from="mcsx:components/theme-switch"/>
                <ThemeSwitch onToggle={cycleTheme}/>
                """);

        assertTrue(McsxLinter.lint(document, ThemeStressController.class, NATIVE_TAGS).isEmpty());
    }

    /** A {@code <for>} variable is in scope for the subtree, and only there. */
    @Test
    void loopVariablesResolveInsideTheLoopAndNotOutside() {
        assertTrue(McsxLinter.lint(McsxParser.parseDocument(
                "<div><for each={nav} as=\"item\"><text>{{item.name}}</text></for></div>"),
                GlassController.class, NATIVE_TAGS).isEmpty());

        List<String> escaped = McsxLinter.lint(McsxParser.parseDocument(
                "<div><for each={nav} as=\"item\"><text>x</text></for><text>{{item.name}}</text></div>"),
                GlassController.class, NATIVE_TAGS);
        assertTrue(escaped.stream().anyMatch(p -> p.contains("item")), escaped.toString());

        List<String> premature = McsxLinter.lint(McsxParser.parseDocument(
                "<for each={item.children} as=\"item\"><text>x</text></for>"),
                GlassController.class, NATIVE_TAGS);
        assertTrue(premature.stream().anyMatch(p -> p.contains("item")), premature.toString());
    }

    @Test
    void controllerFieldsAreNotAcceptedAsHandlers() {
        List<String> problems = McsxLinter.lint(
                McsxParser.parseDocument("<button onClick={enabled}/>"),
                DemoController.class, NATIVE_TAGS);

        assertTrue(problems.stream().anyMatch(p -> p.contains("enabled")), problems.toString());
    }

    /** A {@code <state>} name is in scope for the owner's DESCENDANTS — the runtime hands the state
     *  scope to the child context only — and not for the owner's own attributes. */
    @Test
    void stateNamesResolveForDescendantsAndNotForTheOwnerItself() {
        assertTrue(McsxLinter.lint(McsxParser.parseDocument("""
                <div>
                    <state name="marked" initial="false"/>
                    <div toggle={marked}><if cond={marked}><text>x</text></if></div>
                </div>
                """), GlassController.class, NATIVE_TAGS).isEmpty());

        List<String> onOwner = McsxLinter.lint(McsxParser.parseDocument("""
                <div toggle={marked}>
                    <state name="marked" initial="false"/>
                </div>
                """), GlassController.class, NATIVE_TAGS);
        assertTrue(onOwner.stream().anyMatch(p -> p.contains("marked")), onOwner.toString());
    }

    private static void assertNoProblems(Path file, List<String> problems) {
        assertTrue(problems.isEmpty(),
                () -> file + " has lint problems:\n  " + String.join("\n  ", problems));
    }

    private static McsxDocument parse(Path file) {
        try {
            return McsxParser.parseDocument(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".mcsx".length());
    }

    private static Path resolveImport(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return null;
        }
        String relative = "assets/" + id.substring(0, colon) + "/ui/"
                + id.substring(colon + 1) + ".mcsx";
        for (Path root : RESOURCE_ROOTS) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> filesIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".mcsx")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory, e);
        }
    }
}
