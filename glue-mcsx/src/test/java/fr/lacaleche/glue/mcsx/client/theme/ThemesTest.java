package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThemesTest {

    @Test
    void resourceHandleIsStableAndRemovedResourcesPublishMcsx() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "stable-handle");
        Value<Theme> first = Themes.resource(id);
        Value<Theme> second = Themes.resource(id);
        Theme installed = Theme.builder().set(ThemeTokens.ACCENT, 0xff123456).build();

        assertSame(first, second);
        assertSame(Themes.mcsx(), first.get());
        Themes.install(Map.of(id, installed));
        assertSame(installed, first.get());
        Themes.install(Map.of());
        assertSame(Themes.mcsx(), first.get());
    }

    @Test
    void darkIsCompatibilityAliasForCanonicalMcsxTheme() {
        assertSame(Themes.mcsx(), Themes.dark());
        assertSame(ThemeTokens.SURFACE.fallback(), Themes.mcsx().get(ThemeTokens.SURFACE));
        assertSame(
                ThemeTokens.SURFACE_QUIET_HOVER.fallback(),
                Themes.mcsx().get(ThemeTokens.SURFACE_QUIET_HOVER)
        );
        assertSame(ThemeTokens.CONTROL_ON.fallback(), Themes.mcsx().get(ThemeTokens.CONTROL_ON));
        assertSame(ThemeTokens.DOCK_METRICS.fallback(), Themes.mcsx().get(ThemeTokens.DOCK_METRICS));
    }

    @Test
    void listenerFailureDoesNotLeaveHandlesOnMixedGenerations() {
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "theme-first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "theme-second");
        Value<Theme> first = Themes.resource(firstId);
        Value<Theme> second = Themes.resource(secondId);
        Theme firstTheme = Theme.builder().set(ThemeTokens.ACCENT, 1).build();
        Theme secondTheme = Theme.builder().set(ThemeTokens.ACCENT, 2).build();
        Subscription failing = first.subscribe(value -> {
            throw new IllegalStateException("consumer failed");
        });

        try {
            assertThrows(IllegalStateException.class, () -> Themes.install(Map.of(
                    firstId, firstTheme,
                    secondId, secondTheme
            )));
        } finally {
            failing.close();
        }

        assertSame(firstTheme, first.get());
        assertSame(secondTheme, second.get());
    }

    @Test
    void listenersObserveOneCompleteInstalledGeneration() {
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "atomic-theme-first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "atomic-theme-second");
        Value<Theme> first = Themes.resource(firstId);
        Value<Theme> second = Themes.resource(secondId);
        Theme firstTheme = Theme.builder().set(ThemeTokens.ACCENT, 11).build();
        Theme secondTheme = Theme.builder().set(ThemeTokens.ACCENT, 22).build();
        Theme[] observedSecond = new Theme[1];
        Subscription subscription = first.subscribe(ignored -> observedSecond[0] = second.get());

        try {
            Themes.install(Map.of(firstId, firstTheme, secondId, secondTheme));
        } finally {
            subscription.close();
        }

        assertSame(secondTheme, observedSecond[0]);
    }

    @Test
    void validateAcceptsACompleteThemeDocumentWithoutItsParent() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "valid-theme");

        assertDoesNotThrow(() -> Themes.validate(id, new StringReader("""
                {
                  "parent": "test:base",
                  "values": {
                    "surface": "#18202c",
                    "surface-raised": "@surface",
                    "accent": {"color": "#4d8cff", "alpha": 224},
                    "control-height": 48,
                    "corner-radius": "@control-height"
                  }
                }
                """)));
    }

    @Test
    void validateRejectsMalformedDocumentsNamingResourceAndReason() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "invalid-theme");

        IllegalArgumentException unknownToken = assertThrows(IllegalArgumentException.class,
                () -> Themes.validate(id, new StringReader("{\"values\": {\"unknown\": 1}}")));
        assertTrue(unknownToken.getMessage().contains("test:invalid-theme"), unknownToken.getMessage());
        assertTrue(unknownToken.getMessage().contains("unknown theme token"), unknownToken.getMessage());

        IllegalArgumentException syntax = assertThrows(IllegalArgumentException.class,
                () -> Themes.validate(id, new StringReader("not json")));
        assertTrue(syntax.getMessage().contains("invalid JSON"), syntax.getMessage());
    }

    @Test
    void validateDetectsReferenceCyclesAmongOwnValues() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "cyclic-theme");

        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
                () -> Themes.validate(id, new StringReader(
                        "{\"values\": {\"surface\": \"@surface-raised\", \"surface-raised\": \"@surface\"}}")));
        assertTrue(cycle.getMessage().contains("reference cycle"), cycle.getMessage());
    }
}
