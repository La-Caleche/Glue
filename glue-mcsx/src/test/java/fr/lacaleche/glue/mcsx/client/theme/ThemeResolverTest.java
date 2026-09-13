package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeDefinition;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeJsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemeResolverTest {

    @Test
    void inheritedReferencesObserveChildOverridesAfterMerge() {
        ResourceLocation parent = id("parent");
        ResourceLocation child = id("child");
        Map<ResourceLocation, ThemeDefinition> definitions = new LinkedHashMap<>();
        definitions.put(parent, parse(parent, """
                {"values": {"surface": "@accent", "control-height": "@corner-radius"}}
                """));
        definitions.put(child, parse(child, """
                {"parent": "test:parent", "values": {"accent": "#123456", "corner-radius": 19}}
                """));

        Theme theme = ThemeResolver.resolve(definitions).get(child);

        assertEquals(0xff123456, theme.get(ThemeTokens.SURFACE));
        assertEquals(19, theme.get(ThemeTokens.CONTROL_HEIGHT));
    }

    @Test
    void inheritsAndOverridesCompoundDockMetrics() {
        ResourceLocation resource = id("metrics");
        Theme theme = ThemeResolver.resolve(Map.of(resource, parse(resource, """
                {"values": {"dock-metrics": {
                  "gutter": 1, "splitter-size": 2, "header-height": 3,
                  "corner-radius": 4, "border-width": 5, "tab-text-size": 6,
                  "control-text-size": 7, "tab-padding": 8, "icon-gap": 9,
                  "tab-close-width": 10, "control-width": 11
                }}}
                """))).get(resource);

        assertEquals(new DockMetrics(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
                theme.get(ThemeTokens.DOCK_METRICS));
    }

    @Test
    void resolvesAlphaAndDerivesCheckboxIndicatorFromFinalColors() {
        ResourceLocation resource = id("derived");
        Theme theme = ThemeResolver.resolve(Map.of(resource, parse(resource, """
                {"values": {
                  "accent": "#112233",
                  "control-on": "#223344",
                  "text-primary": "#445566",
                  "text-muted": {"color": "@accent", "alpha": 64},
                  "surface": {"color": "@accent", "alpha": 128}
                }}
                """))).get(resource);

        assertEquals(0x80112233, theme.get(ThemeTokens.SURFACE));
        assertEquals(0x40112233, theme.get(ThemeTokens.TEXT_MUTED));
        assertArrayEquals(
                ThemeTokens.checkboxIndicator(0xff223344, 0xff445566, 0x40112233).getColors(),
                theme.get(ThemeTokens.CHECKBOX_INDICATOR).getColors()
        );
    }

    @Test
    void accentOnlyThemeRetainsLegacyCheckboxIndicatorColor() {
        ResourceLocation resource = id("accent-only");
        Theme theme = ThemeResolver.resolve(Map.of(resource, parse(resource, """
                {"values": {"accent": "#123456"}}
                """))).get(resource);

        assertArrayEquals(
                ThemeTokens.checkboxIndicator(
                        0xff123456,
                        theme.get(ThemeTokens.TEXT_PRIMARY),
                        theme.get(ThemeTokens.TEXT_MUTED)
                ).getColors(),
                theme.get(ThemeTokens.CHECKBOX_INDICATOR).getColors()
        );
    }

    @Test
    void rejectsParentAndTokenCyclesAndMissingParents() {
        ResourceLocation first = id("first");
        ResourceLocation second = id("second");
        IllegalArgumentException parentCycle = assertThrows(IllegalArgumentException.class, () -> ThemeResolver.resolve(Map.of(
                first, parse(first, "{\"parent\": \"test:second\", \"values\": {}}"),
                second, parse(second, "{\"parent\": \"test:first\", \"values\": {}}")
        )));
        assertTrue(parentCycle.getMessage().contains("parent cycle"));

        IllegalArgumentException tokenCycle = assertThrows(IllegalArgumentException.class, () -> ThemeResolver.resolve(Map.of(
                first, parse(first, "{\"values\": {\"surface\": \"@accent\", \"accent\": \"@surface\"}}")
        )));
        assertTrue(tokenCycle.getMessage().contains("token reference cycle"));

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> ThemeResolver.resolve(Map.of(
                first, parse(first, "{\"parent\": \"test:missing\", \"values\": {}}")
        )));
        assertTrue(missing.getMessage().contains("test:missing"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static ThemeDefinition parse(ResourceLocation resource, String json) {
        return ThemeJsonParser.parse(resource, new StringReader(json));
    }
}
