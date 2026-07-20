package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing a pane must not forget where it was: {@link DockOps#closePane} moves it into the
 * layout's closed map and {@link DockOps#openPane} puts it back — same leaf, same split side and
 * share, same floating frame — instead of spawning a fresh cascaded window.
 */
class DockPaneMemoryTest {

    private final DockIds ids = new DockIds();

    @Test
    void closedTabReturnsToItsLeafAndIndex() {
        DockLeaf leaf = new DockLeaf(ids.next("leaf"), List.of("console", "assets", "log"), "assets");
        DockLayout layout = new DockLayout(leaf, List.of());

        DockLayout closed = DockOps.closePane(layout, "assets");
        assertFalse(DockOps.openSet(closed).contains("assets"));
        assertTrue(closed.closed().containsKey("assets"));

        DockLayout reopened = DockOps.openPane(closed, ids, "assets", 800, 600);
        DockLeaf back = (DockLeaf) reopened.tree();
        assertEquals(List.of("console", "assets", "log"), back.tabs());
        assertEquals("assets", back.active());
        assertTrue(reopened.closed().isEmpty());
        assertTrue(reopened.floats().isEmpty());
    }

    @Test
    void closedSoleTabReturnsBesideItsSiblingWithItsShare() {
        DockLeaf inspector = DockLeaf.of(ids, List.of("inspector"));
        DockLeaf viewport = DockLeaf.of(ids, List.of("viewport"));
        DockSplit tree = DockSplit.of(ids, Dir.ROW, List.of(viewport, inspector), List.of(0.79, 0.21));
        DockLayout layout = new DockLayout(tree, List.of());

        DockLayout closed = DockOps.closePane(layout, "inspector");
        // the emptied leaf pruned away: the tree collapsed to the sibling
        assertInstanceOf(DockLeaf.class, closed.tree());
        DockClosed ghost = closed.closed().get("inspector");
        assertEquals(DockClosed.Kind.BESIDE, ghost.kind());
        assertEquals(List.of("viewport"), ghost.besidePanes());
        assertEquals(DropZone.RIGHT, ghost.zone());
        assertEquals(0.21, ghost.share(), 1e-9);

        DockLayout reopened = DockOps.openPane(closed, ids, "inspector", 800, 600);
        DockSplit back = assertInstanceOf(DockSplit.class, reopened.tree());
        assertEquals(Dir.ROW, back.dir());
        assertEquals(List.of("viewport"), ((DockLeaf) back.children().get(0)).tabs());
        assertEquals(List.of("inspector"), ((DockLeaf) back.children().get(1)).tabs());
        assertEquals(0.21, back.sizes().get(1), 1e-9);
        assertTrue(reopened.floats().isEmpty());
    }

    @Test
    void closedFloatReturnsAtItsFrame() {
        DockLeaf leaf = DockLeaf.of(ids, List.of("profiler"));
        DockFloat window = new DockFloat(ids.next("float"), leaf, 40, 60, 380, 280, 1);
        DockLayout layout = new DockLayout(null, List.of(window));

        DockLayout closed = DockOps.closePane(layout, "profiler");
        assertTrue(closed.floats().isEmpty());
        assertEquals(DockClosed.Kind.FLOAT, closed.closed().get("profiler").kind());

        DockLayout reopened = DockOps.openPane(closed, ids, "profiler", 800, 600);
        DockFloat back = reopened.floats().getFirst();
        assertEquals(40, back.x());
        assertEquals(60, back.y());
        assertEquals(380, back.w());
        assertEquals(280, back.h());
    }

    @Test
    void closingAFloatWindowRemembersEveryPaneInIt() {
        DockLeaf leaf = new DockLeaf(ids.next("leaf"), List.of("console", "log"), "console");
        DockFloat window = new DockFloat(ids.next("float"), leaf, 10, 20, 300, 200, 1);
        DockLayout layout = new DockLayout(null, List.of(window));

        DockLayout closed = DockOps.closeFloatRemembering(layout, window.id());
        assertTrue(closed.floats().isEmpty());
        assertEquals(Set.of("console", "log"), closed.closed().keySet());

        DockLayout reopened = DockOps.openPane(closed, ids, "log", 800, 600);
        DockFloat back = reopened.floats().getFirst();
        assertEquals(10, back.x());
        assertEquals(300, back.w());
    }

    @Test
    void rootPaneReturnsAsTheTree() {
        DockLayout layout = new DockLayout(DockLeaf.of(ids, List.of("viewport")), List.of());
        DockLayout closed = DockOps.closePane(layout, "viewport");
        assertNull(closed.tree());
        assertEquals(DockClosed.Kind.ROOT, closed.closed().get("viewport").kind());

        DockLayout reopened = DockOps.openPane(closed, ids, "viewport", 800, 600);
        assertEquals(List.of("viewport"), ((DockLeaf) reopened.tree()).tabs());
    }

    @Test
    void ghostWithNoSurvivingAnchorFallsBackToACascadedWindow() {
        DockLeaf a = DockLeaf.of(ids, List.of("a"));
        DockLeaf b = DockLeaf.of(ids, List.of("b"));
        DockLayout layout = new DockLayout(
                DockSplit.of(ids, Dir.ROW, List.of(a, b), List.of(0.5, 0.5)), List.of());
        DockLayout closed = DockOps.closePane(layout, "a");
        // the anchor pane closes too: nothing left to restore beside
        closed = DockOps.closePane(closed, "b");

        DockLayout reopened = DockOps.openPane(closed, ids, "a", 800, 600);
        assertEquals(1, reopened.floats().size());
        assertEquals(List.of("a"), ((DockLeaf) reopened.floats().getFirst().node()).tabs());
    }

    @Test
    void togglePaneRoundTripRestoresTheLayoutShape() {
        DockLeaf hierarchy = DockLeaf.of(ids, List.of("hierarchy"));
        DockLeaf viewport = DockLeaf.of(ids, List.of("viewport"));
        DockLayout layout = new DockLayout(
                DockSplit.of(ids, Dir.ROW, List.of(hierarchy, viewport), List.of(0.24, 0.76)),
                List.of());

        DockLayout toggled = DockOps.togglePane(layout, ids, "hierarchy", 800, 600);
        DockLayout back = DockOps.togglePane(toggled, ids, "hierarchy", 800, 600);

        DockSplit tree = assertInstanceOf(DockSplit.class, back.tree());
        assertEquals(List.of("hierarchy"), ((DockLeaf) tree.children().get(0)).tabs());
        assertEquals(0.24, tree.sizes().get(0), 1e-9);
        assertTrue(back.floats().isEmpty());
        assertTrue(back.closed().isEmpty());
    }

    @Test
    void closedMemorySurvivesTheCodecRoundTrip() {
        DockLayout layout = new DockLayout(DockLeaf.of(ids, List.of("viewport")), List.of(), Map.of(
                "console", DockClosed.tabbed("log", 1),
                "inspector", DockClosed.beside(List.of("viewport"), DropZone.RIGHT, 0.21),
                "profiler", DockClosed.floating(40, 60, 380, 280),
                "solo", DockClosed.root()));

        DockLayout back = DockLayoutCodec.read(DockLayoutCodec.write(layout), new DockIds());
        assertEquals(DockClosed.tabbed("log", 1), back.closed().get("console"));
        assertEquals(DockClosed.beside(List.of("viewport"), DropZone.RIGHT, 0.21),
                back.closed().get("inspector"));
        assertEquals(DockClosed.floating(40, 60, 380, 280), back.closed().get("profiler"));
        assertEquals(DockClosed.root(), back.closed().get("solo"));
    }

    @Test
    void sanitizeDropsUnknownAndAlreadyOpenClosedEntries() {
        DockLayout layout = new DockLayout(DockLeaf.of(ids, List.of("viewport")), List.of(), Map.of(
                "viewport", DockClosed.root(),          // also open: the open pane wins
                "ghost", DockClosed.root(),             // unknown pane
                "console", DockClosed.tabbed("viewport", 0)));

        DockLayout clean = DockOps.sanitize(layout, Set.of("viewport", "console"));
        assertEquals(Set.of("console"), clean.closed().keySet());
    }
}
