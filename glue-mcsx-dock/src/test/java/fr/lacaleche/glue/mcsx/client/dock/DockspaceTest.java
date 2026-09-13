package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.persistence.DockLayoutStore;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.theme.DockMetrics;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockspaceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void paneIdsAreStableValidatedKeys() {
        DockContent content = context -> null;

        assertEquals("tools.log", pane("tools.log", Component.literal("Log"), content).id());
        assertThrows(IllegalArgumentException.class,
                () -> DockPane.builder("Tools", Component.literal("Tools"), content));
        assertThrows(IllegalArgumentException.class,
                () -> DockPane.builder("../tools", Component.literal("Tools"), content));
    }

    @Test
    void paneBuilderAppliesPresentationDefaultsAndRejectsNullOptions() {
        DockContent content = context -> null;
        DockPane pane = DockPane.builder("tools", Component.literal("Tools"), content).build();
        Component icon = Component.literal("T");
        DockContent trailingHeader = context -> null;
        DockPane customized = DockPane.builder("custom", Component.literal("Custom"), content)
                .icon(icon)
                .closable(false)
                .transparent(true)
                .trailingHeader(trailingHeader)
                .build();

        assertEquals(Component.literal("Tools"), pane.title());
        assertTrue(pane.closable());
        assertFalse(pane.transparent());
        assertNull(pane.icon());
        assertNull(pane.trailingHeader());
        assertEquals(icon, customized.icon());
        assertFalse(customized.closable());
        assertTrue(customized.transparent());
        assertEquals(trailingHeader, customized.trailingHeader());
        assertThrows(NullPointerException.class,
                () -> DockPane.builder("tools", Component.literal("Tools"), content).icon(null));
        assertThrows(NullPointerException.class,
                () -> DockPane.builder("tools", Component.literal("Tools"), content).trailingHeader(null));
    }

    @Test
    void builtInThemesDefineDockPaletteAndMetricsExplicitly() {
        assertNotEquals(Themes.dark().get(ThemeTokens.DOCK_BACKGROUND),
                Themes.light().get(ThemeTokens.DOCK_BACKGROUND));
        assertNotEquals(Themes.dark().get(ThemeTokens.DOCK_PANE_BACKGROUND),
                Themes.light().get(ThemeTokens.DOCK_PANE_BACKGROUND));
        assertTrue(Themes.dark().get(ThemeTokens.DOCK_METRICS).headerHeight() > 0);
        assertTrue(Themes.light().get(ThemeTokens.DOCK_METRICS).splitterSize() > 0);
        assertEquals(12, Themes.mcsx().get(ThemeTokens.DOCK_METRICS).gutter());
        assertEquals(12, Themes.mcsx().get(ThemeTokens.DOCK_METRICS).splitterSize());
        assertEquals(0, Themes.mcsx().get(ThemeTokens.DOCK_METRICS).borderWidth());
        for (Theme theme : List.of(Themes.dark(), Themes.light())) {
            DockMetrics metrics = theme.get(ThemeTokens.DOCK_METRICS);
            assertTrue(metrics.tabTextSize() > 0);
            assertTrue(metrics.controlTextSize() > 0);
            assertTrue(metrics.tabCloseWidth() > 0);
            assertTrue(metrics.controlWidth() > 0);
        }
    }

    @Test
    void translucentDockTokensCarryTheirOwnAlphaRatherThanDerivingIt() {
        assertEquals(0xE8, Themes.dark().get(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND) >>> 24);
        assertEquals(0x5c, Themes.dark().get(ThemeTokens.DOCK_DROP_HIGHLIGHT) >>> 24);
        assertNotEquals(Themes.dark().get(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND),
                Themes.light().get(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND));
        assertThrows(IllegalArgumentException.class, () -> ThemeTokens.withAlpha(0, 256));
    }

    @Test
    void builderRejectsDuplicatePanesAndKeepsRegistrationOrderInFallback() {
        DockLayoutStore store = this.store();
        DockPane first = pane("first", Component.literal("First"), context -> null);
        DockPane second = pane("second", Component.literal("Second"), context -> null);

        assertThrows(IllegalArgumentException.class, () -> Dockspace.builder(id("duplicate"))
                .layoutStore(store)
                .persistence(false)
                .pane(first)
                .pane(first)
                .build());

        DockLayout fallback = store.load(id("ordered"), false, null, null, List.of("first", "second"));
        assertEquals(List.of("first", "second"), ((DockTabs) fallback.tree()).tabs());
    }

    @Test
    void layoutBeforeMountingReportsTheSanitizedJavaDefault() {
        Dockspace dockspace = Dockspace.builder(id("premount"))
                .layoutStore(this.store())
                .persistence(false)
                .pane(pane("known", Component.literal("Known"), context -> null))
                .defaultLayout(DockLayouts.layout(DockLayouts.tabs("known", "removed")))
                .build();

        assertEquals(List.of("known"), ((DockTabs) dockspace.layout().tree()).tabs());
        assertEquals(Set.of("known"), dockspace.openPanes());
    }

    @Test
    void layoutBeforeMountingUsesRegisteredPanesWithoutAConfiguredDefault() {
        Dockspace dockspace = Dockspace.builder(id("premount_fallback"))
                .layoutStore(this.store())
                .pane(pane("first", Component.literal("First"), context -> null))
                .pane(pane("second", Component.literal("Second"), context -> null))
                .build();

        assertEquals(List.of("first", "second"), ((DockTabs) dockspace.layout().tree()).tabs());
        assertEquals(Set.of("first", "second"), dockspace.openPanes());
    }

    @Test
    void layoutBeforeMountingLeavesAConfiguredResourceDefaultUnresolved() {
        Dockspace dockspace = Dockspace.builder(id("premount_resource"))
                .layoutStore(this.store())
                .pane(pane("known", Component.literal("Known"), context -> null))
                .defaultLayout(id("resource_default"))
                .build();

        assertEquals(DockLayouts.empty(), dockspace.layout());
        assertTrue(dockspace.openPanes().isEmpty());
    }

    @Test
    void aWorkspaceThatHasNeverOpenedReportsItselfClosed() {
        Dockspace dockspace = Dockspace.builder(id("unopened"))
                .layoutStore(this.store())
                .persistence(false)
                .pane(pane("known", Component.literal("Known"), context -> null))
                .build();

        assertFalse(dockspace.isOpen());
        assertThrows(IllegalStateException.class, dockspace::saveLayout);
    }

    @Test
    void maximizeStateStartsEmptyAndUnknownPanesFailBeforeUiDispatch() {
        Dockspace dockspace = Dockspace.builder(id("maximize"))
                .layoutStore(this.store())
                .persistence(false)
                .pane(pane("known", Component.literal("Known"), context -> null))
                .build();

        assertTrue(dockspace.maximizedPane().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> dockspace.maximizePane("unknown"));
        assertTrue(dockspace.maximizedPane().isEmpty());
    }

    private DockLayoutStore store() {
        return new DockLayoutStore(this.temporaryDirectory, ResourceProvider.EMPTY, Runnable::run);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static DockPane pane(String id, Component title, DockContent content) {
        return DockPane.builder(id, title, content).build();
    }
}
