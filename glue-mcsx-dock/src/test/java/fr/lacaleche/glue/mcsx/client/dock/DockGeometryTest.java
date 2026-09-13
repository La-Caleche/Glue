package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockGeometry;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockRect;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockGeometryTest {

    @Test
    void aSingleTabsNodeFillsTheArea() {
        DockTabs tabs = DockLayouts.tabs("one");
        DockGeometry.Solved solved = DockGeometry.solve(tabs, new DockRect(10, 20, 300, 200), 8);
        assertEquals(new DockRect(10, 20, 300, 200), solved.tabs().get(tabs));
        assertTrue(solved.splitters().isEmpty());
    }

    @Test
    void childrenAndSplitterRectanglesTileRowsExactly() {
        DockTabs one = DockLayouts.tabs("one");
        DockTabs two = DockLayouts.tabs("two");
        DockTabs three = DockLayouts.tabs("three");
        DockSplit split = new DockSplit(
                DockAxis.HORIZONTAL,
                List.of(one, two, three),
                List.of(0.19, 0.60, 0.21)
        );
        DockGeometry.Solved solved = DockGeometry.solve(split, new DockRect(0, 0, 1000, 500), 8);

        DockRect first = solved.tabs().get(one);
        DockRect middle = solved.tabs().get(two);
        DockRect last = solved.tabs().get(three);
        assertEquals(first.right() + 8, middle.x());
        assertEquals(middle.right() + 8, last.x());
        assertEquals(1000, last.right());
        assertEquals(new DockRect(first.right(), 0, 8, 500), solved.splitters().getFirst().rect());
        assertEquals(split, solved.splitters().getFirst().split());
        assertEquals(0, solved.splitters().getFirst().index());
    }

    @Test
    void nestedVerticalSplitsStayWithinTheirParentSlot() {
        DockTabs side = DockLayouts.tabs("side");
        DockTabs top = DockLayouts.tabs("top");
        DockTabs bottom = DockLayouts.tabs("bottom");
        DockSplit column = new DockSplit(DockAxis.VERTICAL, List.of(top, bottom), List.of(0.7, 0.3));
        DockSplit root = new DockSplit(DockAxis.HORIZONTAL, List.of(side, column), List.of(0.25, 0.75));

        DockGeometry.Solved solved = DockGeometry.solve(root, new DockRect(0, 0, 800, 600), 8);
        DockRect topRect = solved.tabs().get(top);
        DockRect bottomRect = solved.tabs().get(bottom);
        assertEquals(topRect.x(), bottomRect.x());
        assertEquals(topRect.bottom() + 8, bottomRect.y());
        assertEquals(600, bottomRect.bottom());
        assertEquals(solved.tabs().get(side).right() + 8, topRect.x());
    }

    @Test
    void degenerateAreasNeverProduceNegativeChildSizes() {
        DockTabs one = DockLayouts.tabs("one");
        DockTabs two = DockLayouts.tabs("two");
        DockSplit split = DockLayouts.row(one, two);
        DockGeometry.Solved solved = DockGeometry.solve(split, new DockRect(0, 0, 4, 100), 8);
        assertTrue(solved.tabs().get(one).width() >= 0);
        assertTrue(solved.tabs().get(two).width() >= 0);
        assertThrows(IllegalArgumentException.class,
                () -> DockGeometry.solve(split, new DockRect(0, 0, 100, 100), -1));
    }

    @Test
    void solvedCollectionsAreImmutableAndUseNodeIdentity() {
        DockTabs first = DockLayouts.tabs("same");
        DockTabs equalButDistinct = DockLayouts.tabs("same");
        DockGeometry.Solved solved = DockGeometry.solve(first, new DockRect(0, 0, 100, 100), 8);
        assertEquals(new DockRect(0, 0, 100, 100), solved.tabs().get(first));
        assertNull(solved.tabs().get(equalButDistinct));
        assertThrows(UnsupportedOperationException.class,
                () -> solved.tabs().put(equalButDistinct, new DockRect(0, 0, 1, 1)));
    }
}
