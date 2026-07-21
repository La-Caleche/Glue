package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockLayoutCodecTest {

    private final DockIds ids = new DockIds();

    @Test
    void roundTripPreservesStructureSharesAndWindows() {
        DockLeaf viewport = DockLeaf.of(ids, List.of("viewport"));
        DockLeaf console = new DockLeaf(ids.next("leaf"), List.of("console", "assets"), "assets");
        DockSplit tree = DockSplit.of(ids, Dir.COL, List.of(viewport, console), List.of(0.72, 0.28));
        DockFloat window = new DockFloat(ids.next("float"),
                DockLeaf.of(ids, List.of("profiler")), 40, 60, 380, 280, 3);
        DockLayout layout = new DockLayout(tree, List.of(window));

        DockLayout back = DockLayoutCodec.read(DockLayoutCodec.write(layout), new DockIds());

        DockSplit tb = assertInstanceOf(DockSplit.class, back.tree());
        assertEquals(Dir.COL, tb.dir());
        assertEquals(0.72, tb.sizes().get(0), 1e-9);
        DockLeaf cb = assertInstanceOf(DockLeaf.class, tb.children().get(1));
        assertEquals(List.of("console", "assets"), cb.tabs());
        assertEquals("assets", cb.active());
        assertEquals(1, back.floats().size());
        DockFloat fb = back.floats().getFirst();
        assertEquals(40, fb.x());
        assertEquals(280, fb.h());
        assertEquals(3, fb.z());
        assertEquals(List.of("profiler"), ((DockLeaf) fb.node()).tabs());
    }

    @Test
    void emptyLayoutRoundTrips() {
        DockLayout back = DockLayoutCodec.read(DockLayoutCodec.write(DockLayout.empty()), ids);
        assertNull(back.tree());
        assertTrue(back.floats().isEmpty());
    }

    @Test
    void unknownFieldsAndFutureVersionsAreIgnored() {
        String json = """
                {"version":9,"someday":true,"tree":
                  {"type":"leaf","tabs":["a"],"active":"a","badge":3},
                 "floats":[]}
                """;
        DockLayout layout = DockLayoutCodec.read(json, ids);
        assertEquals(List.of("a"), ((DockLeaf) layout.tree()).tabs());
    }

    @Test
    void staleActiveAndDuplicateTabsAreRepaired() {
        String json = """
                {"version":1,"tree":{"type":"leaf","tabs":["a","a","b"],"active":"gone"},"floats":[]}
                """;
        DockLeaf leaf = (DockLeaf) DockLayoutCodec.read(json, ids).tree();
        assertEquals(List.of("a", "b"), leaf.tabs());
        assertEquals("a", leaf.active());
    }

    @Test
    void sizeCountMismatchFallsBackToEvenShares() {
        String json = """
                {"version":1,"tree":{"type":"split","dir":"row","sizes":[0.9],
                  "children":[{"type":"leaf","tabs":["a"],"active":"a"},
                              {"type":"leaf","tabs":["b"],"active":"b"}]},"floats":[]}
                """;
        DockSplit split = (DockSplit) DockLayoutCodec.read(json, ids).tree();
        assertEquals(0.5, split.sizes().get(0), 1e-9);
    }

    @Test
    void degenerateSplitsArePrunedOnLoad() {
        String json = """
                {"version":1,"tree":{"type":"split","dir":"row","sizes":[1.0],
                  "children":[{"type":"leaf","tabs":["only"],"active":"only"}]},"floats":[]}
                """;
        assertInstanceOf(DockLeaf.class, DockLayoutCodec.read(json, ids).tree());
    }

    @Test
    void garbageThrowsInsteadOfReturningHalfALayout() {
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read("not json", ids));
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read("[1,2,3]", ids));
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read(
                "{\"tree\":{\"type\":\"mystery\"}}", ids));
        assertThrows(DockLayoutException.class, () -> DockLayoutCodec.read(
                "{\"tree\":null,\"floats\":[]} trailing", ids));
    }

    @Test
    void escapedStringsSurviveTheTrip() {
        DockLeaf leaf = DockLeaf.of(ids, List.of("pane \"quoted\"\nline"));
        DockLayout back = DockLayoutCodec.read(
                DockLayoutCodec.write(new DockLayout(leaf, List.of())), new DockIds());
        assertEquals("pane \"quoted\"\nline", ((DockLeaf) back.tree()).tabs().getFirst());
    }

    /** A child dropped at read time takes its share with it; the survivors keep their proportions. */
    @Test
    void droppedChildKeepsSurvivingSharesRenormalized() {
        String json = """
                {"tree":{"type":"split","dir":"row",
                  "children":[
                    {"type":"leaf","tabs":["a"],"active":"a"},
                    {"type":"leaf","tabs":["b"],"active":"b"},
                    null],
                  "sizes":[0.7,0.2,0.1]},
                 "floats":[]}
                """;
        DockSplit tree = assertInstanceOf(DockSplit.class, DockLayoutCodec.read(json, ids).tree());
        assertEquals(2, tree.children().size());
        assertEquals(0.7 / 0.9, tree.sizes().get(0), 1e-9,
                "shares must renormalize, not fall back to even");
        assertEquals(0.2 / 0.9, tree.sizes().get(1), 1e-9);
    }

    /** An empty leaf is pruned after the read; its share must vanish with it the same way. */
    @Test
    void prunedEmptyLeafKeepsSurvivingSharesRenormalized() {
        String json = """
                {"tree":{"type":"split","dir":"row",
                  "children":[
                    {"type":"leaf","tabs":["a"],"active":"a"},
                    {"type":"leaf","tabs":["b"],"active":"b"},
                    {"type":"leaf","tabs":[],"active":null}],
                  "sizes":[0.7,0.2,0.1]},
                 "floats":[]}
                """;
        DockSplit tree = assertInstanceOf(DockSplit.class, DockLayoutCodec.read(json, ids).tree());
        assertEquals(2, tree.children().size());
        assertEquals(0.7 / 0.9, tree.sizes().get(0), 1e-9);
        assertEquals(0.2 / 0.9, tree.sizes().get(1), 1e-9);
    }
}
