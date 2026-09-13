package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.style.StylesheetParser;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudioResourcesTest {

    private static final String[] OBSOLETE_PALETTE = {
            "#ffb454",
            "#6496ff",
            "#ffd095",
            "#e89b3f"
    };
    private static final ResourceLocation WORKSPACE = ResourceLocation.fromNamespaceAndPath(
            "glue-test",
            "glue-studio"
    );
    private static final ResourceLocation THEME = ResourceLocation.fromNamespaceAndPath(
            "glue-test",
            "studio"
    );

    @Test
    void packagedStudioStylesheetParses() throws IOException {
        String stylesheet = resource("assets/glue-test/mcsx/styles/glue-studio.mcss");
        StylesheetParser.parse(WORKSPACE, stylesheet);
        assertNoObsoletePalette("glue-studio.mcss", stylesheet);
        assertFalse(stylesheet.contains("#"), "Studio stylesheet must use semantic theme tokens");
    }

    @Test
    void packagedStudioLayoutParses() throws IOException {
        assertNotNull(DockLayouts.parse(resource("assets/glue-test/mcsx/dock/glue-studio.json")));
    }

    @Test
    void packagedStudioThemeParses() throws IOException {
        String theme = resource("assets/glue-test/mcsx/themes/studio.json");
        Themes.validate(THEME, new StringReader(theme));
        assertNoObsoletePalette("studio.json", theme);

        String normalized = theme.replaceAll("\\s+", "");
        assertFalse(normalized.contains("\"parent\""), "Studio must inherit the parentless MCSX baseline");
        assertFalse(normalized.contains("\"dock-border\""), "Studio must not restore dock borders");
        assertEquals(
                "{\"values\":{\"dock-background\":{\"color\":\"#0b0c0d\",\"alpha\":0}}}",
                normalized,
                "Studio may override only the transparent neutral dock background"
        );
    }

    private static void assertNoObsoletePalette(String name, String source) {
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String color : OBSOLETE_PALETTE) {
            assertFalse(normalized.contains(color), () -> name + " still uses obsolete palette color " + color);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = StudioResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing test resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
