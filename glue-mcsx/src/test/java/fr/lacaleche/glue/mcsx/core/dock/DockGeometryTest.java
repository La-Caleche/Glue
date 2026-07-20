package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockGeometry.Solved;
import fr.lacaleche.glue.mcsx.core.dock.DockGeometry.Splitter;
import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockGeometryTest {

    private final DockIds ids = new DockIds();

    @Test
    void aLoneLeafFillsTheArea() {
        DockLeaf leaf = DockLeaf.of(ids, List.of("a"));
        Solved solved = DockGeometry.solve(leaf, new DockRect(10, 20, 300, 200), 8);
        assertEquals(new DockRect(10, 20, 300, 200), solved.leaves().get(leaf.id()));
        assertTrue(solved.splitters().isEmpty());
    }

    @Test
    void anEmptyTreeSolvesToNothing() {
        Solved solved = DockGeometry.solve(null, new DockRect(0, 0, 100, 100), 8);
        assertTrue(solved.leaves().isEmpty());
        assertTrue(solved.splitters().isEmpty());
    }

    @Test
    void rowChildrenAndSplittersTileTheExtentExactly() {
        DockLeaf a = DockLeaf.of(ids, List.of("a"));
        DockLeaf b = DockLeaf.of(ids, List.of("b"));
        DockLeaf c = DockLeaf.of(ids, List.of("c"));
        DockSplit split = DockSplit.of(ids, Dir.ROW, List.of(a, b, c), List.of(0.19, 0.60, 0.21));
        Solved solved = DockGeometry.solve(split, new DockRect(0, 0, 1000, 500), 8);

        DockRect ra = solved.leaves().get(a.id());
        DockRect rb = solved.leaves().get(b.id());
        DockRect rc = solved.leaves().get(c.id());
        assertEquals(0, ra.x());
        assertEquals(ra.right() + 8, rb.x());
        assertEquals(rb.right() + 8, rc.x());
        assertEquals(1000, rc.right(), "last child absorbs rounding so the row tiles exactly");
        assertEquals(500, ra.h());

        List<Splitter> splitters = solved.splitters();
        assertEquals(2, splitters.size());
        assertEquals(split.id(), splitters.get(0).splitId());
        assertEquals(0, splitters.get(0).index());
        assertEquals(new DockRect(ra.right(), 0, 8, 500), splitters.get(0).rect());
    }

    @Test
    void nestedSplitsSolveInTheirParentsSlot() {
        DockLeaf viewport = DockLeaf.of(ids, List.of("viewport"));
        DockLeaf console = DockLeaf.of(ids, List.of("console"));
        DockSplit column = DockSplit.of(ids, Dir.COL, List.of(viewport, console), List.of(0.72, 0.28));
        DockLeaf side = DockLeaf.of(ids, List.of("side"));
        DockSplit root = DockSplit.of(ids, Dir.ROW, List.of(side, column), List.of(0.25, 0.75));
        Solved solved = DockGeometry.solve(root, new DockRect(0, 0, 800, 600), 8);

        DockRect rv = solved.leaves().get(viewport.id());
        DockRect rc = solved.leaves().get(console.id());
        assertEquals(rv.x(), rc.x());
        assertEquals(rv.bottom() + 8, rc.y());
        assertEquals(600, rc.bottom());
        assertEquals(solved.leaves().get(side.id()).right() + 8, rv.x());
    }

    @Test
    void degenerateAreasClampToZeroInsteadOfGoingNegative() {
        DockLeaf a = DockLeaf.of(ids, List.of("a"));
        DockLeaf b = DockLeaf.of(ids, List.of("b"));
        DockSplit split = DockSplit.of(ids, Dir.ROW, List.of(a, b), List.of(0.5, 0.5));
        Solved solved = DockGeometry.solve(split, new DockRect(0, 0, 4, 100), 8);
        assertTrue(solved.leaves().get(a.id()).w() >= 0);
        assertTrue(solved.leaves().get(b.id()).w() >= 0);
    }
}
