package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockOpsTest {

    private final DockIds ids = new DockIds();

    /** The POC's default workspace: hierarchy | (viewport / console+assets) | inspector+profiler. */
    private DockLayout defaultLayout() {
        DockLeaf hierarchy = DockLeaf.of(ids, List.of("hierarchy"));
        DockLeaf viewport = DockLeaf.of(ids, List.of("viewport"));
        DockLeaf console = DockLeaf.of(ids, List.of("console", "assets"));
        DockLeaf inspector = DockLeaf.of(ids, List.of("inspector", "profiler"));
        DockSplit center = DockSplit.of(ids, Dir.COL, List.of(viewport, console), List.of(0.72, 0.28));
        DockSplit root = DockSplit.of(ids, Dir.ROW,
                List.of(hierarchy, center, inspector), List.of(0.19, 0.60, 0.21));
        return new DockLayout(root, List.of());
    }

    private static DockLeaf leafOf(DockLayout layout, String paneId) {
        DockLeaf[] found = new DockLeaf[1];
        walk(layout.tree(), paneId, found);
        for (DockFloat f : layout.floats()) {
            walk(f.node(), paneId, found);
        }
        return found[0];
    }

    private static void walk(DockNode node, String paneId, DockLeaf[] found) {
        if (node instanceof DockLeaf leaf && leaf.tabs().contains(paneId)) {
            found[0] = leaf;
        } else if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                walk(child, paneId, found);
            }
        }
    }

    @Test
    void openSetSeesDockedAndFloatingPanes() {
        DockLayout layout = DockOps.toggleFloat(defaultLayout(), ids, "extra", 900, 560);
        assertEquals(Set.of("hierarchy", "viewport", "console", "assets", "inspector", "profiler", "extra"),
                DockOps.openSet(layout));
    }

    @Test
    void detachRemovesTabAndPromotesNeighbour() {
        DockLayout layout = DockOps.detach(defaultLayout(), "console");
        DockLeaf survivor = leafOf(layout, "assets");
        assertEquals(List.of("assets"), survivor.tabs());
        assertEquals("assets", survivor.active());
    }

    @Test
    void detachingALeafsOnlyTabCollapsesTheSplitAndRenormalizes() {
        DockLayout layout = DockOps.detach(defaultLayout(), "viewport");
        // the COL split had (viewport 0.72, console 0.28); the survivor takes its place entirely
        DockSplit root = assertInstanceOf(DockSplit.class, layout.tree());
        assertEquals(3, root.children().size());
        assertInstanceOf(DockLeaf.class, root.children().get(1));
        assertEquals(List.of("console", "assets"),
                ((DockLeaf) root.children().get(1)).tabs());
    }

    @Test
    void detachingEverythingLeavesAnEmptyWorkspace() {
        DockLayout layout = defaultLayout();
        for (String pane : List.of("hierarchy", "viewport", "console", "assets", "inspector", "profiler")) {
            layout = DockOps.detach(layout, pane);
        }
        assertNull(layout.tree());
        assertTrue(layout.floats().isEmpty());
    }

    @Test
    void detachFixesActiveToThePreviousTab() {
        DockLayout layout = defaultLayout();
        DockLeaf inspector = leafOf(layout, "profiler");
        layout = DockOps.setActive(layout, inspector.id(), "profiler");
        layout = DockOps.detach(layout, "profiler");
        assertEquals("inspector", leafOf(layout, "inspector").active());
    }

    @Test
    void pruneRenormalizesSurvivingShares() {
        DockLeaf a = DockLeaf.of(ids, List.of("a"));
        DockLeaf b = DockLeaf.of(ids, List.of("b"));
        DockLeaf empty = new DockLeaf(ids.next("leaf"), List.of(), null);
        DockSplit split = DockSplit.of(ids, Dir.ROW, List.of(a, empty, b), List.of(0.5, 0.25, 0.25));
        DockSplit pruned = assertInstanceOf(DockSplit.class, DockOps.prune(split));
        assertEquals(2, pruned.children().size());
        assertEquals(0.5 / 0.75, pruned.sizes().get(0), 1e-9);
        assertEquals(0.25 / 0.75, pruned.sizes().get(1), 1e-9);
    }

    @Test
    void pruneReturnsUntouchedSubtreesIdentically() {
        DockLayout layout = defaultLayout();
        assertSame(layout.tree(), DockOps.prune(layout.tree()));
    }

    @Test
    void wrapPutsTheNewcomerOnTheNamedSideAtAThird() {
        DockLeaf existing = DockLeaf.of(ids, List.of("a"));
        DockLeaf added = DockLeaf.of(ids, List.of("b"));
        DockSplit left = DockOps.wrap(ids, existing, added, DropZone.LEFT);
        assertEquals(Dir.ROW, left.dir());
        assertSame(added, left.children().get(0));
        assertEquals(List.of(0.34, 0.66), left.sizes());

        DockSplit bottom = DockOps.wrap(ids, existing, added, DropZone.BOTTOM);
        assertEquals(Dir.COL, bottom.dir());
        assertSame(added, bottom.children().get(1));
        assertEquals(List.of(0.66, 0.34), bottom.sizes());
    }

    @Test
    void adjustSplitClampsBothNeighboursToTheMinimum() {
        DockLayout layout = defaultLayout();
        DockSplit root = (DockSplit) layout.tree();
        DockLayout dragged = DockOps.adjustSplit(layout, root.id(), 0, 0.001);
        DockSplit after = (DockSplit) dragged.tree();
        assertEquals(DockOps.MIN_RATIO, after.sizes().get(0), 1e-9);
        assertEquals(0.19 + 0.60 - DockOps.MIN_RATIO, after.sizes().get(1), 1e-9);
        assertEquals(0.21, after.sizes().get(2), 1e-9);

        DockLayout wide = DockOps.adjustSplit(layout, root.id(), 0, 5.0);
        assertEquals(0.19 + 0.60 - DockOps.MIN_RATIO, ((DockSplit) wide.tree()).sizes().get(0), 1e-9);
    }

    @Test
    void adjustSplitLeavesAnUndersizedPairValid() {
        DockLeaf a = DockLeaf.of(ids, List.of("a"));
        DockLeaf b = DockLeaf.of(ids, List.of("b"));
        DockLeaf c = DockLeaf.of(ids, List.of("c"));
        DockSplit split = DockSplit.of(ids, Dir.ROW, List.of(a, b, c), List.of(0.01, 0.01, 0.98));
        DockLayout layout = new DockLayout(split, List.of());

        DockLayout adjusted = DockOps.adjustSplit(layout, split.id(), 0, 0.5);

        assertEquals(List.of(0.01, 0.01, 0.98), ((DockSplit) adjusted.tree()).sizes());
    }

    @Test
    void droppingALeafsOnlyTabOnItselfIsANoOp() {
        DockLayout layout = defaultLayout();
        DockLeaf viewport = leafOf(layout, "viewport");
        DropTarget self = new DropTarget(Kind.LEAF, viewport.id(), DropZone.CENTER);
        assertSame(layout, DockOps.dropTab(layout, ids, "viewport", viewport.id(), self, 0, 0));
    }

    @Test
    void centerDropAppendsTheTabAndActivatesIt() {
        DockLayout layout = defaultLayout();
        DockLeaf target = leafOf(layout, "inspector");
        DropTarget drop = new DropTarget(Kind.LEAF, target.id(), DropZone.CENTER);
        DockLayout after = DockOps.dropTab(layout, ids, "hierarchy", leafOf(layout, "hierarchy").id(),
                drop, 0, 0);
        DockLeaf merged = leafOf(after, "hierarchy");
        assertEquals(List.of("inspector", "profiler", "hierarchy"), merged.tabs());
        assertEquals("hierarchy", merged.active());
        // the emptied hierarchy leaf is pruned from the root split
        assertEquals(2, ((DockSplit) after.tree()).children().size());
    }

    @Test
    void edgeDropWrapsTheTargetLeaf() {
        DockLayout layout = defaultLayout();
        DockLeaf target = leafOf(layout, "viewport");
        DropTarget drop = new DropTarget(Kind.LEAF, target.id(), DropZone.RIGHT);
        DockLayout after = DockOps.dropTab(layout, ids, "hierarchy", leafOf(layout, "hierarchy").id(),
                drop, 0, 0);
        DockLeaf moved = leafOf(after, "hierarchy");
        assertEquals(List.of("hierarchy"), moved.tabs());
        // viewport's slot is now a ROW split with hierarchy on the right at a third
        DockSplit root = (DockSplit) after.tree();
        DockSplit column = assertInstanceOf(DockSplit.class, root.children().get(0));
        DockSplit wrapped = assertInstanceOf(DockSplit.class, column.children().get(0));
        assertEquals(Dir.ROW, wrapped.dir());
        assertEquals(List.of(0.66, 0.34), wrapped.sizes());
    }

    @Test
    void rootDropWrapsTheWholeTree() {
        DockLayout layout = defaultLayout();
        DropTarget drop = new DropTarget(Kind.ROOT, null, DropZone.TOP);
        DockLayout after = DockOps.dropTab(layout, ids, "console", leafOf(layout, "console").id(),
                drop, 0, 0);
        DockSplit root = (DockSplit) after.tree();
        assertEquals(Dir.COL, root.dir());
        assertEquals(List.of("console"), ((DockLeaf) root.children().get(0)).tabs());
    }

    @Test
    void dropWithoutTargetSpawnsAFloatAtThePointer() {
        DockLayout layout = defaultLayout();
        DockLayout after = DockOps.dropTab(layout, ids, "assets", leafOf(layout, "assets").id(),
                null, 500, 300);
        assertEquals(1, after.floats().size());
        DockFloat window = after.floats().getFirst();
        assertEquals(List.of("assets"), ((DockLeaf) window.node()).tabs());
        assertEquals(300 - 16, window.y());
    }

    @Test
    void dropIntoEmptyTreeBecomesTheTree() {
        DockLayout empty = DockOps.toggleFloat(DockLayout.empty(), ids, "console", 900, 560);
        DockFloat window = empty.floats().getFirst();
        DockLayout docked = DockOps.dropFloat(empty, ids, window.id(),
                new DropTarget(Kind.ROOT, null, DropZone.CENTER));
        assertEquals(List.of("console"), ((DockLeaf) docked.tree()).tabs());
        assertTrue(docked.floats().isEmpty());
    }

    @Test
    void centerDroppingAFloatMergesAllItsTabs() {
        DockLayout layout = defaultLayout();
        layout = DockOps.dropTab(layout, ids, "console", leafOf(layout, "console").id(), null, 100, 100);
        DockFloat window = layout.floats().getFirst();
        DockLeaf target = leafOf(layout, "inspector");
        DockLayout after = DockOps.dropFloat(layout, ids, window.id(),
                new DropTarget(Kind.LEAF, target.id(), DropZone.CENTER));
        assertTrue(after.floats().isEmpty());
        assertEquals(List.of("inspector", "profiler", "console"), leafOf(after, "console").tabs());
        assertEquals("console", leafOf(after, "console").active());
    }

    @Test
    void multiLeafFloatsDoNotRedock() {
        DockLayout layout = defaultLayout();
        DockLeaf a = DockLeaf.of(ids, List.of("x"));
        DockLeaf b = DockLeaf.of(ids, List.of("y"));
        DockFloat window = new DockFloat(ids.next("float"),
                DockSplit.even(ids, Dir.ROW, List.of(a, b)), 0, 0, 400, 300, 1);
        DockLayout floated = layout.withFloats(List.of(window));
        assertSame(floated, DockOps.dropFloat(floated, ids, window.id(),
                new DropTarget(Kind.ROOT, null, DropZone.LEFT)));
    }

    @Test
    void bringToFrontRaisesAboveEveryOtherWindow() {
        DockLayout layout = DockLayout.empty();
        layout = DockOps.toggleFloat(layout, ids, "a", 900, 560);
        layout = DockOps.toggleFloat(layout, ids, "b", 900, 560);
        DockFloat first = layout.floats().get(0);
        assertTrue(layout.floats().get(1).z() > first.z());
        DockLayout raised = DockOps.bringToFront(layout, first.id());
        assertTrue(DockOps.findFloat(raised, first.id()).z() > raised.floats().get(1).z());
    }

    @Test
    void toggleFloatClosesAnOpenPaneWhereverItIs() {
        DockLayout layout = DockOps.toggleFloat(defaultLayout(), ids, "viewport", 900, 560);
        assertNotEquals(true, DockOps.openSet(layout).contains("viewport"));
    }

    @Test
    void sanitizeDropsUnknownAndDuplicateTabs() {
        DockLeaf good = DockLeaf.of(ids, List.of("known", "ghost"));
        DockLeaf dupe = DockLeaf.of(ids, List.of("known", "other"));
        DockSplit tree = DockSplit.even(ids, Dir.ROW, List.of(good, dupe));
        DockLayout cleaned = DockOps.sanitize(new DockLayout(tree, List.of()),
                Set.of("known", "other"));
        assertEquals(Set.of("known", "other"), DockOps.openSet(cleaned));
        assertEquals(List.of("known"), leafOf(cleaned, "known").tabs());
    }

    /** Two floats tied at max z (a hand-edited file) must still be able to pass each other. */
    @Test
    void bringToFrontResolvesAZTie() {
        DockFloat a = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 0, 0, 100, 100, 2);
        DockFloat b = new DockFloat("f-b", DockLeaf.of(ids, List.of("two")), 0, 0, 100, 100, 2);
        DockLayout layout = new DockLayout(null, List.of(a, b));
        DockLayout raised = DockOps.bringToFront(layout, "f-a");
        assertTrue(DockOps.findFloat(raised, "f-a").z() > DockOps.findFloat(raised, "f-b").z());
    }

    @Test
    void bringToFrontIsANoOpForTheUniqueTopWindow() {
        DockFloat a = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 0, 0, 100, 100, 1);
        DockFloat b = new DockFloat("f-b", DockLeaf.of(ids, List.of("two")), 0, 0, 100, 100, 2);
        DockLayout layout = new DockLayout(null, List.of(a, b));
        assertSame(layout, DockOps.bringToFront(layout, "f-b"));
    }

    /** Every load path runs sanitize, so colliding z values must come out dense and unique. */
    @Test
    void sanitizeNormalizesCollidingZOrders() {
        DockFloat a = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 0, 0, 100, 100, 2);
        DockFloat b = new DockFloat("f-b", DockLeaf.of(ids, List.of("two")), 0, 0, 100, 100, 2);
        DockFloat c = new DockFloat("f-c", DockLeaf.of(ids, List.of("three")), 0, 0, 100, 100, 7);
        DockLayout cleaned = DockOps.sanitize(new DockLayout(null, List.of(a, b, c)),
                Set.of("one", "two", "three"));
        Set<Integer> zs = new java.util.HashSet<>();
        for (DockFloat f : cleaned.floats()) {
            zs.add(f.z());
        }
        assertEquals(Set.of(1, 2, 3), zs);
        assertEquals(3, DockOps.findFloat(cleaned, "f-c").z(), "stable: highest z stays on top");
    }

    /** The min-visible strip on each axis, matching what DockHostView passes (FloatWindowView.HEADER_H). */
    private static final int MIN_VIS_X = 40;
    private static final int MIN_VIS_Y = 30;

    /** A frame stored on a larger window must clamp so drag math agrees with what is rendered. */
    @Test
    void clampFloatsPullsOffscreenFramesBackOnStage() {
        DockFloat off = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 3500, -50, 360, 260, 1);
        DockFloat on = new DockFloat("f-b", DockLeaf.of(ids, List.of("two")), 100, 100, 360, 260, 2);
        DockLayout layout = new DockLayout(null, List.of(off, on));
        DockLayout clamped = DockOps.clampFloats(layout, 1280, 720, MIN_VIS_X, MIN_VIS_Y);
        assertEquals(1280 - MIN_VIS_X, DockOps.findFloat(clamped, "f-a").x());
        assertEquals(0, DockOps.findFloat(clamped, "f-a").y());
        assertEquals(100, DockOps.findFloat(clamped, "f-b").x());
    }

    @Test
    void clampFloatsIsIdentityWhenEverythingIsOnStage() {
        DockFloat on = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 100, 100, 360, 260, 1);
        DockLayout layout = new DockLayout(null, List.of(on));
        assertSame(layout, DockOps.clampFloats(layout, 1280, 720, MIN_VIS_X, MIN_VIS_Y));
    }

    /**
     * A stage smaller than the min-visible strip has no room to satisfy the clamp, so every float
     * collapses to the origin. This is why the caller must not clamp before it has been laid out:
     * a 0x0 stage would pile the whole workspace at (0,0).
     */
    @Test
    void clampFloatsAgainstADegenerateStagePilesEverythingAtTheOrigin() {
        DockFloat a = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 300, 200, 360, 260, 1);
        DockFloat b = new DockFloat("f-b", DockLeaf.of(ids, List.of("two")), 900, 400, 360, 260, 2);
        DockLayout layout = new DockLayout(null, List.of(a, b));
        DockLayout clamped = DockOps.clampFloats(layout, 0, 0, MIN_VIS_X, MIN_VIS_Y);
        for (DockFloat f : clamped.floats()) {
            assertEquals(0, f.x());
            assertEquals(0, f.y());
        }
    }

    /** Windows opened from the panels menu cascade; two of them must never land on one another. */
    @Test
    void toggleFloatCascadesEachNewWindow() {
        DockLayout one = DockOps.toggleFloat(new DockLayout(null, List.of()), ids, "a", 1280, 720);
        DockLayout two = DockOps.toggleFloat(one, ids, "b", 1280, 720);
        List<DockFloat> floats = two.floats();
        assertEquals(2, floats.size());
        assertNotEquals(
                List.of(floats.get(0).x(), floats.get(0).y()),
                List.of(floats.get(1).x(), floats.get(1).y()));
    }

    /**
     * The frames a mutation does not target must survive it byte for byte. DockHostView renders a
     * clamped view of the layout while persisting the raw one, which only holds together if a tab
     * close or a raise cannot move a window the user never touched.
     */
    @Test
    void nonFrameMutationsLeaveEveryFloatFrameAlone() {
        DockFloat a = new DockFloat("f-a", DockLeaf.of(ids, List.of("one")), 3500, -50, 360, 260, 1);
        DockFloat b = new DockFloat("f-b", DockLeaf.of(ids, List.of("two", "three")), 100, 100, 360, 260, 2);
        DockLayout layout = new DockLayout(DockLeaf.of(ids, List.of("docked")), List.of(a, b));

        assertFramesUnchanged(a, DockOps.detach(layout, "docked"), "f-a");
        assertFramesUnchanged(a, DockOps.bringToFront(layout, "f-b"), "f-a");
        DockLeaf leaf = (DockLeaf) DockOps.findFloat(layout, "f-b").node();
        assertFramesUnchanged(a, DockOps.setActive(layout, leaf.id(), "three"), "f-a");
    }

    private static void assertFramesUnchanged(DockFloat before, DockLayout after, String floatId) {
        DockFloat now = DockOps.findFloat(after, floatId);
        assertEquals(before.x(), now.x());
        assertEquals(before.y(), now.y());
        assertEquals(before.w(), now.w());
        assertEquals(before.h(), now.h());
    }
}
