package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeDefinition;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeJsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemeJsonParserTest {

    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath("test", "theme");

    @Test
    void parsesColorDimensionReferenceAlphaAndDockMetricsForms() {
        ThemeDefinition definition = this.parse("""
                {
                  "parent": "test:base",
                  "values": {
                    "surface": "#abc",
                    "surface-raised": "#123456",
                    "accent": {"color": "@surface", "alpha": 128},
                    "control-height": 52,
                    "corner-radius": "@control-height",
                    "dock-metrics": {
                      "gutter": 1,
                      "splitter-size": 2,
                      "header-height": 3,
                      "corner-radius": 4,
                      "border-width": 5,
                      "tab-text-size": 6,
                      "control-text-size": 7,
                      "tab-padding": 8,
                      "icon-gap": 9,
                      "tab-close-width": 10,
                      "control-width": 11
                    }
                  }
                }
                """);

        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "base"), definition.parent());
        assertEquals(new ThemeDefinition.Literal(0xffaabbcc), definition.values().get("surface"));
        assertEquals(new ThemeDefinition.Literal(0xff123456), definition.values().get("surface-raised"));
        assertInstanceOf(ThemeDefinition.Alpha.class, definition.values().get("accent"));
        assertEquals(new ThemeDefinition.Literal(52), definition.values().get("control-height"));
        assertEquals(new ThemeDefinition.Reference("control-height"), definition.values().get("corner-radius"));
        assertEquals(
                new ThemeDefinition.Literal(new DockMetrics(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
                definition.values().get("dock-metrics")
        );
    }

    @Test
    void rejectsUnknownSchemaFieldsTokensAndDerivedToken() {
        assertInvalid("{\"values\": {}, \"extra\": true}", "unknown field");
        assertInvalid("{\"values\": {\"unknown\": 1}}", "unknown theme token");
        assertInvalid("{\"values\": {\"checkbox-indicator\": \"#fff\"}}", "unknown theme token");
        assertInvalid("{\"parent\": \"test:base\"}", "required field 'values'");
    }

    @Test
    void rejectsTypeMismatchesAndInvalidNumbers() {
        assertInvalid("{\"values\": {\"accent\": \"@control-height\"}}", "cannot reference dimension");
        assertInvalid("{\"values\": {\"control-height\": \"@accent\"}}", "cannot reference color");
        assertInvalid("{\"values\": {\"control-height\": -1}}", "cannot be negative");
        assertInvalid("{\"values\": {\"control-height\": 1.5}}", "JSON integer");
        assertInvalid("{\"values\": {\"accent\": {\"color\": \"#fff\", \"alpha\": 256}}}", "0..255");
        assertInvalid("{\"values\": {\"accent\": \"transparent\"}}", "requires #rgb");
        assertInvalid("{\"values\": {\"dock-metrics\": \"@dock-metrics\"}}", "requires an object");
        assertInvalid("{\"values\": {\"dock-metrics\": {}}}", "missing field");
        assertInvalid("{\"values\": {\"dock-metrics\": {\"extra\": 1}}}", "unknown field");
        assertInvalid("{\"values\": {\"dock-metrics\": " + metrics("1.5") + "}}", "JSON integer");
        assertInvalid("{\"values\": {\"dock-metrics\": " + metrics("-1") + "}}", "cannot be negative");
    }

    private ThemeDefinition parse(String json) {
        return ThemeJsonParser.parse(RESOURCE, new StringReader(json));
    }

    private static String metrics(String gutter) {
        return "{\"gutter\":" + gutter + ",\"splitter-size\":2,\"header-height\":3,"
                + "\"corner-radius\":4,\"border-width\":5,\"tab-text-size\":6,"
                + "\"control-text-size\":7,\"tab-padding\":8,\"icon-gap\":9,"
                + "\"tab-close-width\":10,\"control-width\":11}";
    }

    private void assertInvalid(String json, String message) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> this.parse(json));
        assertTrue(exception.getMessage().contains("test:theme"));
        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }
}
