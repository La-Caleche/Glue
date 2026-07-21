package fr.lacaleche.glue.mcsx.view;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the bundled icon font, which {@code processResources} extracts from the Font Awesome
 * webjar and renames to {@code assets/mcsx/fonts/icons.ttf}. Two failure modes are silent in-game:
 * the extraction rule breaking (the font vanishes, and {@code FontRegistry} then throws from its
 * static initializer and takes the whole dockspace with it), and a name in {@code icons.json}
 * pointing at a codepoint the font does not actually draw (an empty box, failing nowhere).
 */
class IconFontCoverageTest {

    private static final String FONT = "assets/mcsx/fonts/icons.ttf";
    private static final String DESCRIPTOR = "assets/mcsx/fonts/icons.json";

    @Test
    void theBundledFontAndDescriptorAreOnTheClasspath() {
        assertNotNull(resource(FONT), FONT + " is missing — check the webjar extraction in processResources");
        assertNotNull(resource(DESCRIPTOR), DESCRIPTOR + " is missing");
    }

    @Test
    void everyDeclaredGlyphIsDrawnByTheFont() throws IOException {
        Set<Integer> mapped = mappedCodePoints(readAll(FONT));
        Set<String> missing = new TreeSet<>();
        for (Map.Entry<String, String> entry : declaredGlyphs().entrySet()) {
            if (!mapped.contains(Integer.parseInt(entry.getValue().replaceAll("(?i)^(0x|u\\+)", ""), 16))) {
                missing.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        assertTrue(missing.isEmpty(), "icons.json names glyphs the font does not draw: " + missing);
    }

    /**
     * The close affordance is asked for by name rather than typed as a literal {@code ×}, because
     * the icon font maps U+00D7 itself — a literal renders as a full-em icon several times the size
     * of the surrounding text. If this name ever disappears, the dock's close button breaks.
     */
    @Test
    void theCloseGlyphTheDockChromeAsksForExists() {
        assertTrue(declaredGlyphs().containsKey("close"), "icons.json must define 'close'");
    }

    private static Map<String, String> declaredGlyphs() {
        try (InputStream stream = resource(DESCRIPTOR)) {
            assertNotNull(stream, DESCRIPTOR + " is missing");
            JsonObject root = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            @SuppressWarnings("unchecked")
            Map<String, String> glyphs = new Gson().fromJson(root.get("glyphs"), Map.class);
            return glyphs;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static InputStream resource(String path) {
        return IconFontCoverageTest.class.getClassLoader().getResourceAsStream(path);
    }

    private static byte[] readAll(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            assertNotNull(stream, path + " is missing");
            return stream.readAllBytes();
        }
    }

    /** Minimal TrueType `cmap` format-4 reader — enough to answer "does this font draw U+XXXX?". */
    private static Set<Integer> mappedCodePoints(byte[] font) throws IOException {
        int cmap = tableOffset(font, "cmap");
        Set<Integer> points = new HashSet<>();
        int subtables = u16(font, cmap + 2);
        for (int i = 0; i < subtables; i++) {
            int subtable = cmap + (int) u32(font, cmap + 8 + 8 * i);
            if (u16(font, subtable) != 4) {
                continue;
            }
            int segments = u16(font, subtable + 6) / 2;
            for (int s = 0; s < segments; s++) {
                int end = u16(font, subtable + 14 + 2 * s);
                int start = u16(font, subtable + 16 + segments * 2 + 2 * s);
                if (end == 0xFFFF) {
                    continue;
                }
                for (int cp = start; cp <= end; cp++) {
                    points.add(cp);
                }
            }
        }
        assertEquals(true, points.size() > 100, "cmap parse produced too few codepoints");
        return points;
    }

    private static int tableOffset(byte[] font, String tag) throws IOException {
        int tables = u16(font, 4);
        for (int i = 0; i < tables; i++) {
            int record = 12 + 16 * i;
            String name = new String(font, record, 4, StandardCharsets.ISO_8859_1);
            if (name.equals(tag)) {
                return (int) u32(font, record + 8);
            }
        }
        throw new IOException("no '" + tag + "' table");
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static long u32(byte[] data, int offset) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(data, offset, 4))) {
            return in.readInt() & 0xFFFFFFFFL;
        }
    }
}
