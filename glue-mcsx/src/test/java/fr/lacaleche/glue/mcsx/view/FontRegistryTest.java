package fr.lacaleche.glue.mcsx.view;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontRegistryTest {

    @Test
    void bundledIconDescriptorIsAvailableBeforeResourceReload() {
        FontRegistry fonts = FontRegistry.getInstance();

        assertTrue(fonts.hasGlyph(FontRegistry.DEFAULT_ICONS, "check"));
        assertTrue(fonts.hasGlyph(FontRegistry.DEFAULT_ICONS, "palette"));
        assertEquals(27, fonts.glyphNames(FontRegistry.DEFAULT_ICONS).size());
    }

    @Test
    void bundledIconFontContainsEveryMappedGlyph() throws Exception {
        FontRegistry fonts = FontRegistry.getInstance();
        String path = "assets/mcsx/fonts/icons.ttf";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            for (String name : fonts.glyphNames(FontRegistry.DEFAULT_ICONS)) {
                String glyph = fonts.glyph(FontRegistry.DEFAULT_ICONS, name);
                assertTrue(font.canDisplay(glyph.codePointAt(0)), name);
            }
        }
    }
}
