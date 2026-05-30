package fr.lacaleche.glue.client.registries;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ReloadableRegistryTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("glue", path);
    }

    private static Map<ResourceLocation, String> json(Object... pairs) {
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(id((String) pairs[i]), (String) pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void get_javaOnlyEntry_returnsIt() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "java-a");

        assertEquals("java-a", reg.get(id("a")));
        assertNull(reg.get(id("missing")), "Unknown id should return null");
    }

    @Test
    public void get_jsonOverridesJava_forSameId() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "java-a");
        reg.reload(json("a", "json-a"));

        assertEquals("json-a", reg.get(id("a")), "JSON layer must win over Java for the same id");
    }

    @Test
    public void get_jsonMissing_fallsBackToJava() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "java-a");
        reg.reload(json("b", "json-b"));

        assertEquals("java-a", reg.get(id("a")), "Java entry should remain reachable when JSON lacks the id");
        assertEquals("json-b", reg.get(id("b")));
    }

    @Test
    public void reload_replacesJsonLayer_deletedEntriesDisappear() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("java"), "java");
        reg.reload(json("x", "first"));
        assertEquals("first", reg.get(id("x")));

        reg.reload(json("y", "second")); // 'x' no longer present in the JSON layer

        assertNull(reg.get(id("x")), "A JSON entry removed on reload must disappear");
        assertEquals("second", reg.get(id("y")));
        assertEquals("java", reg.get(id("java")), "Java entries survive reloads");
    }

    @Test
    public void reload_bumpsVersion() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        int before = reg.version();

        reg.reload(json("a", "1"));
        reg.reload(json("a", "2"));

        assertEquals(before + 2, reg.version(), "version() must increment once per reload");
    }

    @Test
    public void containsKey_checksBothLayers() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("java"), "j");
        reg.reload(json("json", "v"));

        assertTrue(reg.containsKey(id("java")));
        assertTrue(reg.containsKey(id("json")));
        assertFalse(reg.containsKey(id("nope")));
    }

    @Test
    public void size_countsDistinctIdsAcrossLayers() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "java-a"); // overlaps with JSON 'a'
        reg.register(id("b"), "java-b");
        reg.reload(json("a", "json-a", "c", "json-c"));

        // Distinct ids: a (shared), b (java), c (json) -> 3
        assertEquals(3, reg.size());
        assertEquals(reg.getAll().size(), reg.size(), "size() must match getAll().size()");
    }

    @Test
    public void getAll_mergesWithJsonPriority() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "java-a");
        reg.register(id("b"), "java-b");
        reg.reload(json("a", "json-a"));

        Map<ResourceLocation, String> all = reg.getAll();
        assertEquals("json-a", all.get(id("a")));
        assertEquals("java-b", all.get(id("b")));
        assertThrows(UnsupportedOperationException.class, () -> all.put(id("c"), "x"),
                "getAll() must be unmodifiable");
    }

    @Test
    public void register_duplicateJava_lastWins() {
        ReloadableRegistry<String> reg = new ReloadableRegistry<>("test");
        reg.register(id("a"), "first");
        String returned = reg.register(id("a"), "second");

        assertEquals("second", returned);
        assertEquals("second", reg.get(id("a")), "A duplicate Java registration overwrites the previous value");
    }
}
