package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a dock tree plus an area into concrete rectangles — the single geometry pass the view
 * layer lays out and hit-tests against. Each split shares the extent left after its splitter gaps
 * per its {@code sizes}; the last child absorbs integer rounding so children and splitters always
 * tile the area exactly.
 */
public final class DockGeometry {

    /** One draggable gap between two split children, identified for {@link DockOps#adjustSplit}. */
    public record Splitter(String splitId, int index, Dir dir, DockRect rect) {
    }

    /** The solved pass: every leaf's and split's rect (in tree order) and every splitter. */
    public record Solved(Map<String, DockRect> leaves, Map<String, DockRect> splits,
                         List<Splitter> splitters) {
    }

    private DockGeometry() {
    }

    public static Solved solve(DockNode tree, DockRect area, int splitterPx) {
        Map<String, DockRect> leaves = new LinkedHashMap<>();
        Map<String, DockRect> splits = new LinkedHashMap<>();
        List<Splitter> splitters = new ArrayList<>();
        if (tree != null) {
            walk(tree, area, splitterPx, leaves, splits, splitters);
        }
        return new Solved(Collections.unmodifiableMap(leaves),
                Collections.unmodifiableMap(splits), List.copyOf(splitters));
    }

    private static void walk(DockNode node, DockRect rect, int splitterPx,
                             Map<String, DockRect> leaves, Map<String, DockRect> splits,
                             List<Splitter> splitters) {
        if (node instanceof DockLeaf leaf) {
            leaves.put(leaf.id(), rect);
            return;
        }
        DockSplit split = (DockSplit) node;
        splits.put(split.id(), rect);
        boolean row = split.dir() == Dir.ROW;
        int count = split.children().size();
        int available = Math.max(0, (row ? rect.w() : rect.h()) - (count - 1) * splitterPx);
        int cursor = row ? rect.x() : rect.y();
        double total = split.sizes().stream().mapToDouble(Double::doubleValue).sum();
        double cumulative = 0;
        int allocated = 0;
        for (int i = 0; i < count; i++) {
            cumulative += split.sizes().get(i);
            int size = i == count - 1
                    ? available - allocated
                    : Math.max(0, (int) Math.round(available * cumulative / total) - allocated);
            DockRect child = row
                    ? new DockRect(cursor, rect.y(), size, rect.h())
                    : new DockRect(rect.x(), cursor, rect.w(), size);
            walk(split.children().get(i), child, splitterPx, leaves, splits, splitters);
            cursor += size;
            allocated += size;
            if (i < count - 1) {
                DockRect gap = row
                        ? new DockRect(cursor, rect.y(), splitterPx, rect.h())
                        : new DockRect(rect.x(), cursor, rect.w(), splitterPx);
                splitters.add(new Splitter(split.id(), i, split.dir(), gap));
                cursor += splitterPx;
            }
        }
    }
}
