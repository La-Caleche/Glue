package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Resolves a semantic dock tree to exact stage rectangles. */
@Environment(EnvType.CLIENT)
public final class DockGeometry {

    public record Splitter(DockSplit split, int index, DockRect rect) {
    }

    public record Solved(Map<DockTabs, DockRect> tabs, Map<DockSplit, DockRect> splits,
                         List<Splitter> splitters) {

        public Solved {
            tabs = Collections.unmodifiableMap(new IdentityHashMap<>(tabs));
            splits = Collections.unmodifiableMap(new IdentityHashMap<>(splits));
            splitters = List.copyOf(splitters);
        }
    }

    private DockGeometry() {
    }

    public static Solved solve(DockNode tree, DockRect area, int splitterSize) {
        if (splitterSize < 0) throw new IllegalArgumentException("Splitter size cannot be negative");

        Map<DockTabs, DockRect> tabs = new IdentityHashMap<>();
        Map<DockSplit, DockRect> splits = new IdentityHashMap<>();
        List<Splitter> splitters = new ArrayList<>();
        if (tree != null) walk(tree, area, splitterSize, tabs, splits, splitters);

        return new Solved(tabs, splits, splitters);
    }

    private static void walk(DockNode node, DockRect rect, int splitterSize,
                             Map<DockTabs, DockRect> tabs, Map<DockSplit, DockRect> splits,
                             List<Splitter> splitters) {
        if (node instanceof DockTabs dockTabs) {
            tabs.put(dockTabs, rect);
            return;
        }

        DockSplit split = (DockSplit) node;
        splits.put(split, rect);
        boolean horizontal = split.axis() == DockAxis.HORIZONTAL;
        int childCount = split.children().size();
        long extent = horizontal ? rect.width() : rect.height();
        long gaps = (long) (childCount - 1) * splitterSize;
        int available = (int) Math.max(0L, extent - gaps);
        int cursor = horizontal ? rect.x() : rect.y();
        double total = split.shares().stream().mapToDouble(Double::doubleValue).sum();
        double cumulative = 0.0;
        int allocated = 0;

        for (int index = 0; index < childCount; index++) {
            cumulative += split.shares().get(index);
            int size = index == childCount - 1
                    ? available - allocated
                    : Math.max(0, (int) Math.round(available * cumulative / total) - allocated);
            DockRect childRect = horizontal
                    ? new DockRect(cursor, rect.y(), size, rect.height())
                    : new DockRect(rect.x(), cursor, rect.width(), size);
            walk(split.children().get(index), childRect, splitterSize, tabs, splits, splitters);
            cursor = Math.addExact(cursor, size);
            allocated += size;
            if (index < childCount - 1) {
                DockRect splitterRect = horizontal
                        ? new DockRect(cursor, rect.y(), splitterSize, rect.height())
                        : new DockRect(rect.x(), cursor, rect.width(), splitterSize);
                splitters.add(new Splitter(split, index, splitterRect));
                cursor = Math.addExact(cursor, splitterSize);
            }
        }
    }
}
