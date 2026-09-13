package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DockModelTest {

    @Test
    void factoriesKeepSemanticLayoutsConcise() {
        DockSplit root = DockLayouts.row(
                DockLayouts.tabs("hierarchy"),
                DockLayouts.column(DockLayouts.tabs("viewport"), DockLayouts.tabs("console", "assets"))
        );
        DockLayout layout = DockLayouts.layout(root);

        assertEquals(DockAxis.HORIZONTAL, ((DockSplit) layout.tree()).axis());
        assertEquals("console", ((DockTabs) ((DockSplit) root.children().get(1)).children().get(1)).active());
    }

    @Test
    void recordsTakeImmutableDefensiveCopies() {
        List<String> paneSource = new ArrayList<>(List.of("one", "two"));
        DockTabs tabs = new DockTabs(paneSource, "one");
        paneSource.add("three");

        List<DockNode> childSource = new ArrayList<>(List.of(tabs, DockLayouts.tabs("three")));
        DockSplit split = new DockSplit(DockAxis.HORIZONTAL, childSource, List.of(0.5, 0.5));
        childSource.clear();

        List<DockWindow> windowSource = new ArrayList<>(List.of(DockLayouts.window(tabs, 0, 0, 100, 80)));
        DockLayout layout = new DockLayout(split, windowSource);
        windowSource.clear();

        assertEquals(List.of("one", "two"), tabs.tabs());
        assertEquals(2, split.children().size());
        assertEquals(1, layout.windows().size());
        assertThrows(UnsupportedOperationException.class, () -> tabs.tabs().add("four"));
    }

    @Test
    void tabsRejectNullBlankDuplicateAndInvalidActivePanes() {
        assertThrows(NullPointerException.class, () -> new DockTabs(null, "one"));
        assertThrows(NullPointerException.class, () -> new DockTabs(List.of("one"), null));
        assertThrows(NullPointerException.class, () -> new DockTabs(java.util.Arrays.asList("one", null), "one"));
        assertThrows(IllegalArgumentException.class, () -> new DockTabs(List.of("one", "one"), "one"));
        assertThrows(IllegalArgumentException.class, () -> new DockTabs(List.of(" "), " "));
        assertThrows(IllegalArgumentException.class, () -> new DockTabs(List.of("one"), "two"));
    }

    @Test
    void splitsRejectNullAxisChildrenAndInvalidShares() {
        DockTabs one = DockLayouts.tabs("one");
        DockTabs two = DockLayouts.tabs("two");

        assertThrows(NullPointerException.class,
                () -> new DockSplit(null, List.of(one, two), List.of(0.5, 0.5)));
        assertThrows(NullPointerException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, java.util.Arrays.asList(one, null), List.of(0.5, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, List.of(one), List.of(1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, List.of(one, two), List.of(1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, List.of(one, two), List.of(0.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, List.of(one, two), List.of(Double.NaN, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new DockSplit(DockAxis.HORIZONTAL, List.of(one, two), List.of(Double.MAX_VALUE, Double.MAX_VALUE)));
    }

    @Test
    void windowsRequireContentAndPositiveDimensions() {
        DockTabs tabs = DockLayouts.tabs("one");
        assertThrows(NullPointerException.class, () -> new DockWindow(null, 0, 0, 10, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new DockWindow(tabs, 0, 0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new DockWindow(tabs, 0, 0, 10, -1, 0));
    }
}
