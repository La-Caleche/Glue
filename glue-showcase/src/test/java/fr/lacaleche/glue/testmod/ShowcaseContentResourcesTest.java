package fr.lacaleche.glue.testmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseContentResourcesTest {

    private static final List<String> BLOCKS = List.of(
            "test_outline",
            "test_spinning",
            "test_shader",
            "test_additive_sprite",
            "test_shape"
    );

    @Test
    void everyDemoBlockHasCompleteResources() throws IOException {
        JsonObject translations = resourceJson("assets/glue-test/lang/en_us.json");
        JsonArray pickaxeBlocks = resourceJson("data/minecraft/tags/block/mineable/pickaxe.json")
                .getAsJsonArray("values");

        for (String block : BLOCKS) {
            assertNotNull(resourceJson("assets/glue-test/blockstates/" + block + ".json"));
            assertNotNull(resourceJson("assets/glue-test/items/" + block + ".json"));
            JsonObject loot = resourceJson("data/glue-test/loot_table/blocks/" + block + ".json");

            assertEquals("glue-test:" + block,
                    loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                            .getAsJsonArray("entries").get(0).getAsJsonObject()
                            .get("name").getAsString());
            assertTrue(translations.has("block.glue-test." + block), "Missing translation for " + block);
            assertTrue(pickaxeBlocks.asList().stream()
                    .anyMatch(value -> value.getAsString().equals("glue-test:" + block)),
                    "Missing pickaxe tag entry for " + block);
        }
    }

    @Test
    void controlAndComponentTranslationsArePackaged() throws IOException {
        JsonObject translations = resourceJson("assets/glue-test/lang/en_us.json");

        assertTrue(translations.has("key.glue-test.open_showcase"));
        assertTrue(translations.has("key.glue-test.toggle_raycast_debug"));
        assertTrue(translations.has("showcase.controls.title"));
        assertTrue(translations.has("showcase.controls.studio"));
        assertTrue(translations.has("showcase.files.title"));
        assertTrue(translations.has("item.glue-test.test_component.tooltip.use"));
        for (int preset = 0; preset < 5; preset++) {
            assertTrue(translations.has("item.glue-test.test_component.preset." + preset));
        }
    }

    @Test
    void descriptorRequiresEveryDirectGlueModule() throws IOException {
        JsonObject dependencies = resourceJson("fabric.mod.json").getAsJsonObject("depends");

        for (String mod : List.of("glue", "glue-render", "glue-lumos", "glue-lumos-client",
                "glue-mcsx", "glue-mcsx-dock", "glue-gametest")) {
            assertTrue(dependencies.has(mod), "Missing required dependency " + mod);
        }
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = ShowcaseContentResourcesTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing showcase resource: " + path);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
