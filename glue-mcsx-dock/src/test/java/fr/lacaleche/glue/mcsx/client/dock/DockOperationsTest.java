package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockOperations;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DropTarget;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockOperationsTest {

    @Test
    void activationDetachPruningAndOpenSetCoverDockedAndFloatingPanes() {
        DockTabs left = DockLayouts.tabs("one", "two");
        DockTabs right = DockLayouts.tabs("three");
        DockTabs floating = DockLayouts.tabs("four");
        DockLayout layout = new DockLayout(
                new DockSplit(DockAxis.HORIZONTAL, List.of(left, right), List.of(0.25, 0.75)),
                List.of(new DockWindow(floating, 20, 30, 200, 150, 1))
        );

        DockLayout activated = DockOperations.activate(layout, left, "two");
        DockTabs active = leafWith(activated, "two");
        assertEquals("two", active.active());
        assertSame(right, ((DockSplit) activated.tree()).children().get(1));
        assertEquals(Set.of("one", "two", "three", "four"), DockOperations.openSet(activated));

        DockLayout detached = DockOperations.detach(activated, "two");
        assertEquals("one", leafWith(detached, "one").active());
        detached = DockOperations.detach(detached, "one");
        assertSame(right, detached.tree());
        detached = DockOperations.detach(detached, "four");
        assertTrue(detached.windows().isEmpty());
    }

    @Test
    void removingEverythingProducesAnEmptyLayout() {
        DockLayout layout = DockLayouts.layout(DockLayouts.row(DockLayouts.tabs("one"), DockLayouts.tabs("two")));
        layout = DockOperations.detach(layout, "one");
        layout = DockOperations.detach(layout, "two");
        assertNull(layout.tree());
    }

    @Test
    void survivingAllZeroSharesBecomeEven() {
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(DockLayouts.tabs("one"), DockLayouts.tabs("two"), DockLayouts.tabs("drop")),
                List.of(0.0, 0.0, 1.0)
        );

        DockLayout sanitized = DockOperations.sanitize(DockLayouts.layout(split), Set.of("one", "two"));
        assertEquals(List.of(0.5, 0.5), ((DockSplit) sanitized.tree()).shares());
    }

    @Test
    void splitAdjustmentClampsNeighboursAndPreservesThePair() {
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(DockLayouts.tabs("one"), DockLayouts.tabs("two"), DockLayouts.tabs("three")),
                List.of(0.2, 0.5, 0.3)
        );
        DockLayout adjusted = DockOperations.adjustSplit(DockLayouts.layout(split), split, 0, -5.0);
        DockSplit result = (DockSplit) adjusted.tree();
        assertEquals(DockOperations.MIN_RATIO, result.shares().get(0), 1.0e-9);
        assertEquals(0.63, result.shares().get(1), 1.0e-9);
        assertEquals(0.3, result.shares().get(2), 1.0e-9);
        assertSame(adjusted, DockOperations.adjustSplit(adjusted, result, 0, Double.NaN));
    }

    @Test
    void activatingThePaneThatIsAlreadyActiveKeepsTheLayoutIdentity() {
        DockTabs tabs = DockLayouts.tabs("one", "two");
        DockLayout layout = DockLayouts.layout(tabs);

        assertSame(layout, DockOperations.activate(layout, tabs, tabs.active()));
        assertSame(layout, DockOperations.activate(layout, tabs, "absent"));
    }

    @Test
    void splitAdjustmentScalesItsMinimumToUnnormalizedShareWeights() {
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(DockLayouts.tabs("one"), DockLayouts.tabs("two")),
                List.of(3.0, 1.0)
        );

        DockSplit result = (DockSplit) DockOperations.adjustSplit(DockLayouts.layout(split), split, 0, -5.0)
                .tree();

        assertEquals(4.0, DockOperations.shareTotal(split), 1.0e-9);
        assertEquals(DockOperations.MIN_RATIO * 4.0, result.shares().get(0), 1.0e-9);
        assertEquals(4.0 - DockOperations.MIN_RATIO * 4.0, result.shares().get(1), 1.0e-9);
    }

    @Test
    void effectiveNoOpsAndEqualTargetsKeepLayoutIdentity() {
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(DockLayouts.tabs("one"), DockLayouts.tabs("two"), DockLayouts.tabs("three")),
                List.of(0.2, 0.5, 0.3)
        );
        DockWindow window = new DockWindow(DockLayouts.tabs("float"), 20, 30, 300, 200, 1);
        DockLayout layout = new DockLayout(split, List.of(window));
        DockWindow equalWindow = new DockWindow(window.node(), 20, 30, 300, 200, 1);

        assertSame(layout, DockOperations.adjustSplit(layout, split, 0, 0.2));
        assertSame(layout, DockOperations.moveFloat(layout, window, 20, 30));
        assertSame(layout, DockOperations.resizeFloat(layout, window, 20, 30, 300, 200));
        assertSame(layout, DockOperations.moveFloat(layout, equalWindow, 40, 50));
        assertSame(layout, DockOperations.raiseFloat(layout, equalWindow));
        assertSame(layout, DockOperations.removeFloat(layout, equalWindow));
    }

    @Test
    void toggleFloatClosesAnOpenPaneAndFloatsAClosedPane() {
        DockLayout open = DockLayouts.layout(DockLayouts.tabs("one"));

        DockLayout closed = DockOperations.toggleFloat(open, "one", 1000, 800);
        assertNull(closed.tree());
        assertTrue(closed.windows().isEmpty());

        DockLayout floated = DockOperations.toggleFloat(closed, "one", 1000, 800);
        DockWindow window = floated.windows().getFirst();
        assertEquals(310, window.x());
        assertEquals(260, window.y());
        assertEquals(List.of("one"), ((DockTabs) window.node()).tabs());
    }

    @Test
    void detachingFlattensAPromotedSplitOnTheSameAxis() {
        DockTabs one = DockLayouts.tabs("one");
        DockTabs removed = DockLayouts.tabs("removed");
        DockTabs two = DockLayouts.tabs("two");
        DockTabs three = DockLayouts.tabs("three");
        DockSplit promoted = new DockSplit(DockAxis.HORIZONTAL, List.of(two, three), List.of(0.25, 0.75));
        DockSplit branch = new DockSplit(DockAxis.VERTICAL, List.of(removed, promoted), List.of(0.4, 0.6));
        DockSplit root = new DockSplit(DockAxis.HORIZONTAL, List.of(one, branch), List.of(0.5, 0.5));

        DockSplit result = (DockSplit) DockOperations.detach(DockLayouts.layout(root), "removed").tree();

        assertEquals(List.of(one, two, three), result.children());
        assertEquals(List.of(0.5, 0.125, 0.375), result.shares());
        assertSame(one, result.children().get(0));
        assertSame(two, result.children().get(1));
        assertSame(three, result.children().get(2));
    }

    @Test
    void creatingAFloatOverMalformedStackingKeepsTheNewWindowOnTop() {
        DockWindow first = new DockWindow(DockLayouts.tabs("one"), 0, 0, 100, 100, 100);
        DockWindow second = new DockWindow(DockLayouts.tabs("two"), 0, 0, 100, 100, 200);
        DockTabs added = DockLayouts.tabs("three");

        DockLayout result = DockOperations.createFloat(
                new DockLayout(null, List.of(first, second)), added, 0, 0, 100, 100);

        assertEquals(List.of(1, 2, 3), result.windows().stream().map(DockWindow::stackingOrder).toList());
        assertSame(first.node(), result.windows().get(0).node());
        assertSame(second.node(), result.windows().get(1).node());
        assertSame(added, result.windows().get(2).node());
    }

    @Test
    void sanitationFlattensSameAxisSplitsWithoutChangingTheirProportions() {
        DockTabs timeline = DockLayouts.tabs("timeline");
        DockTabs viewport = DockLayouts.tabs("viewport");
        DockTabs inspector = DockLayouts.tabs("inspector");
        DockSplit left = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(timeline, viewport),
                List.of(0.25, 0.75)
        );
        DockSplit root = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(left, inspector),
                List.of(0.8, 0.2)
        );

        DockLayout sanitized = DockOperations.sanitize(
                DockLayouts.layout(root),
                Set.of("timeline", "viewport", "inspector")
        );
        DockSplit flattened = (DockSplit) sanitized.tree();

        assertEquals(List.of(timeline, viewport, inspector), flattened.children());
        assertEquals(0.2, flattened.shares().get(0), 1.0e-9);
        assertEquals(0.6, flattened.shares().get(1), 1.0e-9);
        assertEquals(0.2, flattened.shares().get(2), 1.0e-9);

        DockSplit adjusted = (DockSplit) DockOperations.adjustSplit(
                sanitized,
                flattened,
                1,
                0.65
        ).tree();
        assertEquals(0.2, adjusted.shares().get(0), 1.0e-9);
        assertEquals(0.65, adjusted.shares().get(1), 1.0e-9);
        assertEquals(0.15, adjusted.shares().get(2), 1.0e-9);
    }

    @Test
    void edgeDropMergesWithAnExistingSplitOnTheSameAxis() {
        DockTabs timeline = DockLayouts.tabs("timeline");
        DockTabs viewport = DockLayouts.tabs("viewport");
        DockTabs inspector = DockLayouts.tabs("inspector");
        DockSplit existing = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(timeline, viewport),
                List.of(0.25, 0.75)
        );

        DockSplit wrapped = DockOperations.wrap(existing, inspector, DropTarget.Zone.RIGHT);

        assertEquals(List.of(timeline, viewport, inspector), wrapped.children());
        assertEquals(0.165, wrapped.shares().get(0), 1.0e-9);
        assertEquals(0.495, wrapped.shares().get(1), 1.0e-9);
        assertEquals(0.34, wrapped.shares().get(2), 1.0e-9);
    }

    @Test
    void continuousMutationLeavesTheDragStartSnapshotIntactForCancellation() {
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(DockLayouts.tabs("one"), DockLayouts.tabs("two")),
                List.of(0.5, 0.5)
        );
        DockWindow window = new DockWindow(DockLayouts.tabs("float"), 20, 30, 300, 200, 1);
        DockLayout dragStart = new DockLayout(split, List.of(window));

        DockLayout resized = DockOperations.adjustSplit(dragStart, split, 0, 0.7);
        DockWindow current = resized.windows().getFirst();
        DockLayout moved = DockOperations.moveFloat(resized, current, 400, 500);

        assertEquals(List.of(0.5, 0.5), ((DockSplit) dragStart.tree()).shares());
        assertEquals(20, dragStart.windows().getFirst().x());
        assertEquals(400, moved.windows().getFirst().x());
    }

    @Test
    void tabDropsSupportCenterLeafEdgeRootAndFloatingDestinations() {
        DockTabs one = DockLayouts.tabs("one", "spare");
        DockTabs two = DockLayouts.tabs("two");
        DockLayout base = DockLayouts.layout(DockLayouts.row(one, two));

        DockLayout centered = DockOperations.dropTab(base, "spare", one,
                new DropTarget(DropTarget.Kind.TABS, two, DropTarget.Zone.CENTER), 0, 0);
        assertEquals(List.of("two", "spare"), leafWith(centered, "spare").tabs());
        assertEquals("spare", leafWith(centered, "spare").active());

        DockTabs centeredOne = leafWith(centered, "one");
        DockTabs centeredTwo = leafWith(centered, "two");
        DockLayout edged = DockOperations.dropTab(centered, "one", centeredOne,
                new DropTarget(DropTarget.Kind.TABS, centeredTwo, DropTarget.Zone.LEFT), 0, 0);
        DockSplit wrapped = findParentSplit(edged.tree(), leafWith(edged, "one"));
        assertEquals(DockAxis.HORIZONTAL, wrapped.axis());
        assertEquals(0.34, wrapped.shares().getFirst());

        DockTabs moved = leafWith(edged, "one");
        DockLayout rooted = DockOperations.dropTab(edged, "one", moved,
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.TOP), 0, 0);
        assertEquals(DockAxis.VERTICAL, ((DockSplit) rooted.tree()).axis());

        DockTabs spare = leafWith(rooted, "spare");
        DockLayout floated = DockOperations.dropTab(rooted, "spare", spare, null, 500, 300);
        assertEquals(1, floated.windows().size());
        assertEquals(320, floated.windows().getFirst().x());
        assertEquals(284, floated.windows().getFirst().y());
    }

    @Test
    void selfCenterDropReordersAndActivatesWithoutLosingTheLeaf() {
        DockTabs tabs = new DockTabs(List.of("one", "two"), "one");
        DockLayout layout = DockLayouts.layout(tabs);
        DockLayout reordered = DockOperations.dropTab(layout, "one", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER), 0, 0);
        assertEquals(List.of("two", "one"), ((DockTabs) reordered.tree()).tabs());
        assertEquals("one", ((DockTabs) reordered.tree()).active());
    }

    @Test
    void soleTabCannotSplitAgainstTheLeafItWouldRemove() {
        DockTabs tabs = DockLayouts.tabs("one");
        DockLayout layout = DockLayouts.layout(tabs);

        assertSame(layout, DockOperations.dropTab(layout, "one", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.LEFT), 0, 0));
        assertSame(layout, DockOperations.dropTab(layout, "one", tabs,
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.LEFT), 0, 0));
    }

    @Test
    void floatOperationsSanitizeFramesStackingAndSingleTabsRedocking() {
        DockLayout layout = DockLayouts.empty();
        layout = DockOperations.createFloat(layout, DockLayouts.tabs("one"), 20, 30, 0, -2);
        layout = DockOperations.createFloat(layout, DockLayouts.tabs("two", "three"), 60, 70, 200, 150);
        DockWindow first = layout.windows().getFirst();
        assertEquals(1, first.width());
        assertEquals(1, first.height());

        DockLayout moved = DockOperations.moveFloat(layout, first, -100, 900);
        DockWindow movedFirst = moved.windows().getFirst();
        DockLayout resized = DockOperations.resizeFloat(moved, movedFirst, -50, 800, -10, 0);
        DockWindow resizedFirst = resized.windows().getFirst();
        assertEquals(1, resizedFirst.width());
        assertEquals(1, resizedFirst.height());

        DockLayout raised = DockOperations.raiseFloat(resized, resizedFirst);
        assertSame(resizedFirst.node(), raised.windows().getLast().node());
        assertEquals(List.of(1, 2), raised.windows().stream().map(DockWindow::stackingOrder).toList());

        DockWindow tabsWindow = raised.windows().stream()
                .filter(window -> window.node() instanceof DockTabs tabs && tabs.tabs().contains("two"))
                .findFirst().orElseThrow();
        DockLayout docked = DockOperations.dropFloat(raised, tabsWindow,
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.CENTER));
        assertEquals(List.of("two", "three"), ((DockTabs) docked.tree()).tabs());

        DockWindow remaining = docked.windows().getFirst();
        DockLayout removed = DockOperations.removeFloat(docked, remaining);
        assertTrue(removed.windows().isEmpty());
    }

    @Test
    void splitTreeWindowsRedockAtEdgesAndStagePresentationClampsCoordinates() {
        DockWindow splitWindow = new DockWindow(
                DockLayouts.row(DockLayouts.tabs("one"), DockLayouts.tabs("two")),
                2000,
                -20,
                400,
                300,
                7
        );
        DockLayout layout = new DockLayout(DockLayouts.tabs("docked"), List.of(splitWindow));
        DockLayout redocked = DockOperations.dropFloat(layout, splitWindow,
                new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.LEFT));
        assertTrue(redocked.windows().isEmpty());
        DockSplit row = (DockSplit) redocked.tree();
        assertEquals(List.of("one", "two", "docked"), row.children().stream()
                .map(node -> ((DockTabs) node).active())
                .toList());
        assertEquals(List.of(0.17, 0.17, 0.66), row.shares());

        DockLayout presented = DockOperations.presentOnStage(layout, 1280, 720, 40, 30);
        assertEquals(1240, presented.windows().getFirst().x());
        assertEquals(0, presented.windows().getFirst().y());
        assertEquals(400, presented.windows().getFirst().width());
    }

    @Test
    void sanitationDropsUnknownAndCrossTreeDuplicatesAndNormalizesStackingStably() {
        DockTabs docked = DockLayouts.tabs("known", "ghost");
        DockWindow first = new DockWindow(DockLayouts.tabs("known", "other"), 0, 0, 100, 100, 4);
        DockWindow second = new DockWindow(DockLayouts.tabs("last"), 0, 0, 100, 100, 4);
        DockLayout sanitized = DockOperations.sanitize(
                new DockLayout(docked, List.of(first, second)),
                Set.of("known", "other", "last")
        );

        assertEquals(Set.of("known", "other", "last"), DockOperations.openSet(sanitized));
        assertEquals(List.of("other"), ((DockTabs) sanitized.windows().getFirst().node()).tabs());
        assertEquals(List.of(1, 2), sanitized.windows().stream().map(DockWindow::stackingOrder).toList());
        assertSame(second.node(), sanitized.windows().getLast().node());
        assertEquals(2, new HashSet<>(sanitized.windows().stream().map(DockWindow::stackingOrder).toList()).size());
    }

    @Test
    void sameGroupStripDropsReorderToThePointerSlot() {
        DockTabs tabs = new DockTabs(List.of("a", "b", "c"), "a");
        DockLayout layout = DockLayouts.layout(tabs);

        DockLayout reordered = DockOperations.dropTab(layout, "a", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER, 2), 0, 0);
        assertEquals(List.of("b", "a", "c"), ((DockTabs) reordered.tree()).tabs());
        assertEquals("a", ((DockTabs) reordered.tree()).active());

        DockLayout appended = DockOperations.dropTab(layout, "a", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER, 3), 0, 0);
        assertEquals(List.of("b", "c", "a"), ((DockTabs) appended.tree()).tabs());

        // Dropping back onto the tab's own slot changes nothing at all.
        assertSame(layout, DockOperations.dropTab(layout, "a", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER, 0), 0, 0));
        assertSame(layout, DockOperations.dropTab(layout, "a", tabs,
                new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER, 1), 0, 0));
    }

    @Test
    void crossGroupStripDropsInsertAtThePointerSlot() {
        DockTabs source = DockLayouts.tabs("one", "spare");
        DockTabs destination = DockLayouts.tabs("two", "three");
        DockLayout layout = DockLayouts.layout(DockLayouts.row(source, destination));

        DockLayout dropped = DockOperations.dropTab(layout, "spare", source,
                new DropTarget(DropTarget.Kind.TABS, destination, DropTarget.Zone.CENTER, 1), 0, 0);
        DockTabs merged = leafWith(dropped, "spare");
        assertEquals(List.of("two", "spare", "three"), merged.tabs());
        assertEquals("spare", merged.active());
    }

    @Test
    void floatingStripMergesInsertAtThePointerSlotAndKeepTheirActivePane() {
        DockTabs destination = DockLayouts.tabs("a", "b");
        DockWindow window = DockLayouts.window(new DockTabs(List.of("x", "y"), "y"), 0, 0, 200, 150);
        DockLayout layout = new DockLayout(destination, List.of(window));

        DockLayout merged = DockOperations.dropFloat(layout, window,
                new DropTarget(DropTarget.Kind.TABS, destination, DropTarget.Zone.CENTER, 1));
        DockTabs strip = leafWith(merged, "x");
        assertEquals(List.of("a", "x", "y", "b"), strip.tabs());
        assertEquals("y", strip.active());
        assertTrue(merged.windows().isEmpty());
    }

    @Test
    void splitRootedWindowsRefuseCenterMergesButDockWholeAtLeafEdges() {
        DockTabs destination = DockLayouts.tabs("docked");
        DockWindow splitWindow = DockLayouts.window(
                DockLayouts.row(DockLayouts.tabs("one"), DockLayouts.tabs("two")), 0, 0, 400, 300);
        DockLayout layout = new DockLayout(destination, List.of(splitWindow));

        assertSame(layout, DockOperations.dropFloat(layout, splitWindow,
                new DropTarget(DropTarget.Kind.TABS, destination, DropTarget.Zone.CENTER)));

        DockLayout docked = DockOperations.dropFloat(layout, splitWindow,
                new DropTarget(DropTarget.Kind.TABS, destination, DropTarget.Zone.LEFT));
        assertTrue(docked.windows().isEmpty());
        DockSplit wrapped = (DockSplit) docked.tree();
        assertEquals(DockAxis.HORIZONTAL, wrapped.axis());
        assertEquals(List.of("one", "two", "docked"), wrapped.children().stream()
                .map(node -> ((DockTabs) node).active())
                .toList());
    }

    private static DockTabs leafWith(DockLayout layout, String pane) {
        DockTabs tabs = leafWith(layout.tree(), pane);
        if (tabs != null) return tabs;
        for (DockWindow window : layout.windows()) {
            tabs = leafWith(window.node(), pane);
            if (tabs != null) return tabs;
        }
        return null;
    }

    private static DockTabs leafWith(DockNode node, String pane) {
        if (node instanceof DockTabs tabs) return tabs.tabs().contains(pane) ? tabs : null;
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                DockTabs found = leafWith(child, pane);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static DockSplit findParentSplit(DockNode node, DockNode target) {
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                if (child == target) return split;
                DockSplit found = findParentSplit(child, target);
                if (found != null) return found;
            }
        }
        return null;
    }
}
