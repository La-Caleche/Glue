package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.client.style.StylesheetParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The shipped stylesheets outside the studio's own. A stylesheet is only parsed when its screen
 * opens, so a syntax error would otherwise surface as a broken demo at runtime rather than here.
 */
class ShowcaseStylesheetsTest {

    private static final String[] OBSOLETE_PALETTE = {
            "#ffb454",
            "#6496ff",
            "#ffd095",
            "#e89b3f"
    };

    @Test
    void packagedPlaygroundStylesheetParses() throws IOException {
        assertParses("showcase");
    }

    @Test
    void packagedExpeditionPlannerStylesheetParses() throws IOException {
        assertParses("expedition-planner");
    }

    @Test
    void packagedHudOverlayStylesheetParses() throws IOException {
        assertParses("hud-overlay");
    }

    @Test
    void packagedShowcaseControlsStylesheetParses() throws IOException {
        assertParses("showcase-controls");
    }

    @Test
    void packagedRawScopeStylesheetParses() throws IOException {
        assertParses("showcase-raw");
    }

    private static void assertParses(String name) throws IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("glue-test", name);
        String stylesheet = resource("assets/glue-test/mcsx/styles/" + name + ".mcss");
        StylesheetParser.parse(id, stylesheet);

        String normalized = stylesheet.toLowerCase(Locale.ROOT);
        for (String color : OBSOLETE_PALETTE) {
            assertFalse(normalized.contains(color), () -> name + " still uses obsolete palette color " + color);
        }
        assertFalse(
                normalized.contains("#"),
                () -> name + " must use semantic theme tokens instead of palette literals"
        );
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = ShowcaseStylesheetsTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
