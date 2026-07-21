package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.mcsx.DocumentLoader;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxContent;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxDocument;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.style.TailwindParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the testmod's {@code .mcsx} component library: every file under {@code ui/components} must
 * parse, and every {@code class} it declares (on base tags and on {@code <variants>}/{@code <case>})
 * must be valid Tailwind.
 * Data-driven resources aren't checked by the compiler — a typo'd utility or
 * token would only surface in-game. The directory is enumerated rather than listed by hand so a new
 * component cannot ship without coverage.
 */
class ComponentLibraryTest {

    private static final Path COMPONENT_DIR = Path.of("src/main/resources/assets/mcsx/ui/components");

    @Test
    void everyComponentParsesAndUsesValidTailwind() {
        List<String> ids = componentIds();
        assertFalse(ids.isEmpty(), "no components found under " + COMPONENT_DIR);
        for (String id : ids) {
            McsxDocument document = assertDoesNotThrow(
                    () -> DocumentLoader.loadFromClasspath(id), id + " must parse");
            checkClasses(document.root(), id);
        }
    }

    private static List<String> componentIds() {
        try (Stream<Path> files = Files.list(COMPONENT_DIR)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".mcsx"))
                    .map(name -> "mcsx:components/" + name.substring(0, name.length() - ".mcsx".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + COMPONENT_DIR.toAbsolutePath(), e);
        }
    }

    private static void checkClasses(McsxElement element, String id) {
        String classes = element.attribute("class");
        if (classes != null) {
            // Strip {name} interpolation tokens (resolved at bind time) before validating the utilities.
            String literal = classes.replaceAll("\\{[^}]*}", " ").trim();
            assertDoesNotThrow(() -> TailwindParser.parseStrict(literal),
                    id + " has an invalid class: '" + classes + "'");
        }
        for (McsxContent child : element.children()) {
            if (child instanceof McsxElement childElement) {
                checkClasses(childElement, id);
            }
        }
    }
}
