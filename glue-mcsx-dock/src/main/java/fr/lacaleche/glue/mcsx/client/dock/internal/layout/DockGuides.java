package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * The single authority for docking drop geometry. Every guide control pairs the {@link DropTarget}
 * it advertises with the rectangle it occupies, and that one rectangle is what gets painted,
 * hovered, highlighted and hit-tested — so releasing over a painted control always performs exactly
 * the operation the control shows, and a control is only ever emitted for an operation the payload
 * supports.
 */
@Environment(EnvType.CLIENT)
public final class DockGuides {

    public static final int GUIDE_SIZE = 30;
    public static final int GUIDE_GAP = 38;
    public static final int ROOT_GUIDE_INSET = 10;

    /** One drop guide control: {@code rect} is both the painted control and its accepted region. */
    public record Guide(DropTarget target, DockRect rect) {
    }

    private DockGuides() {
    }

    /**
     * The five-control cluster around a leaf's center. A control is emitted only when it fits fully
     * inside the leaf, so a small pane simply offers fewer targets, and the center control is
     * withheld for payloads that cannot merge into a tab strip.
     */
    public static List<Guide> leafGuides(DockTabs tabs, DockRect leaf,
                                         boolean centerAllowed, boolean edgesAllowed) {
        List<Guide> guides = new ArrayList<>(5);
        int centerX = leaf.x() + leaf.width() / 2;
        int centerY = leaf.y() + leaf.height() / 2;
        if (centerAllowed) {
            add(guides, new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.CENTER), centerX, centerY, leaf);
        }
        if (edgesAllowed) {
            add(guides, new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.LEFT),
                    centerX - GUIDE_GAP, centerY, leaf);
            add(guides, new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.RIGHT),
                    centerX + GUIDE_GAP, centerY, leaf);
            add(guides, new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.TOP),
                    centerX, centerY - GUIDE_GAP, leaf);
            add(guides, new DropTarget(DropTarget.Kind.TABS, tabs, DropTarget.Zone.BOTTOM),
                    centerX, centerY + GUIDE_GAP, leaf);
        }
        return guides;
    }

    /**
     * The stage-level controls: one control per edge splitting the whole tree, or — for an empty
     * tree, where there is nothing to split — a single center control that docks the payload as the
     * new tree.
     */
    public static List<Guide> rootGuides(DockRect stage, boolean treeEmpty) {
        List<Guide> guides = new ArrayList<>(4);
        int centerX = stage.x() + stage.width() / 2;
        int centerY = stage.y() + stage.height() / 2;
        if (treeEmpty) {
            add(guides, new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.CENTER),
                    centerX, centerY, stage);
            return guides;
        }

        int edgeCenter = ROOT_GUIDE_INSET + GUIDE_SIZE / 2;
        add(guides, new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.LEFT),
                stage.x() + edgeCenter, centerY, stage);
        add(guides, new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.RIGHT),
                stage.right() - edgeCenter, centerY, stage);
        add(guides, new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.TOP),
                centerX, stage.y() + edgeCenter, stage);
        add(guides, new DropTarget(DropTarget.Kind.ROOT, null, DropTarget.Zone.BOTTOM),
                centerX, stage.bottom() - edgeCenter, stage);
        return guides;
    }

    /** The first guide under the pointer, in list order — callers put higher-priority guides first. */
    public static Guide hit(List<Guide> guides, double x, double y) {
        for (Guide guide : guides) {
            if (guide.rect().contains(x, y)) return guide;
        }
        return null;
    }

    /** Combines guide groups in priority order without retaining visually ambiguous overlaps. */
    public static List<Guide> combine(List<Guide> first, List<Guide> second) {
        List<Guide> combined = new ArrayList<>(first.size() + second.size());
        for (Guide guide : first) addDistinct(combined, guide);
        for (Guide guide : second) addDistinct(combined, guide);
        return combined;
    }

    /**
     * The top-most leaf under the pointer that can accept a drop: the dragged window itself never
     * hosts its own drop, and a floating leaf entirely outside the stage is not a target.
     */
    public static DropTarget.TabsHit topHit(List<DropTarget.TabsHit> hitsTopFirst, double x, double y,
                                            DockRect stage, DockWindow ignoredWindow) {
        for (DropTarget.TabsHit hit : hitsTopFirst) {
            if (ignoredWindow != null && hit.window() == ignoredWindow) continue;
            if (hit.window() != null && !stage.intersects(hit.rect())) continue;
            if (hit.rect().contains(x, y)) return hit;
        }
        return null;
    }

    /**
     * The strip position a pointer inserts at: before the first tab whose midpoint the pointer has
     * not passed, after every tab otherwise.
     */
    public static int insertionIndex(List<DockRect> tabRects, double x) {
        int index = 0;
        for (DockRect rect : tabRects) {
            if (x >= rect.x() + (double) rect.width() / 2) index++;
        }
        return index;
    }

    /** The x coordinate of the insertion caret for {@link #insertionIndex}'s result. */
    public static int insertionX(List<DockRect> tabRects, int index, int stripStart) {
        if (tabRects.isEmpty()) return stripStart;
        if (index >= tabRects.size()) return tabRects.getLast().right();
        return tabRects.get(index).x();
    }

    /** The stage area the advertised operation would give the payload, for the drop preview. */
    public static DockRect previewRect(DropTarget target, DockRect hoveredLeaf,
                                       DockRect stage, int splitterSize,
                                       DockNode existing, DockNode added) {
        if (splitterSize < 0) throw new IllegalArgumentException("Splitter size cannot be negative");

        DockRect base = target.kind() == DropTarget.Kind.ROOT ? stage : hoveredLeaf;
        if (base == null) return null;
        if (target.zone() == DropTarget.Zone.CENTER) return base;
        if (existing == null || added == null) return null;

        DockGeometry.Solved solved = DockGeometry.solve(
                DockOperations.wrap(existing, added, target.zone()), base, splitterSize);
        List<DockTabs> addedTabs = new ArrayList<>();
        collectTabs(added, addedTabs);
        DockRect result = null;
        for (DockTabs tabs : addedTabs) {
            DockRect rect = solved.tabs().get(tabs);
            if (rect != null) result = union(result, rect);
        }
        return result;
    }

    private static void add(List<Guide> guides, DropTarget target, int centerX, int centerY, DockRect within) {
        DockRect rect = new DockRect(centerX - GUIDE_SIZE / 2, centerY - GUIDE_SIZE / 2, GUIDE_SIZE, GUIDE_SIZE);
        if (rect.x() < within.x() || rect.y() < within.y()
                || rect.right() > within.right() || rect.bottom() > within.bottom()) {
            return;
        }
        addDistinct(guides, new Guide(target, rect));
    }

    private static void addDistinct(List<Guide> guides, Guide candidate) {
        for (Guide guide : guides) {
            if (guide.rect().intersects(candidate.rect())) return;
        }
        guides.add(candidate);
    }

    private static void collectTabs(DockNode node, List<DockTabs> tabs) {
        if (node instanceof DockTabs leaf) {
            tabs.add(leaf);
            return;
        }
        for (DockNode child : ((DockSplit) node).children()) {
            collectTabs(child, tabs);
        }
    }

    private static DockRect union(DockRect first, DockRect second) {
        if (first == null) return second;

        int x = Math.min(first.x(), second.x());
        int y = Math.min(first.y(), second.y());
        int right = Math.max(first.right(), second.right());
        int bottom = Math.max(first.bottom(), second.bottom());
        return new DockRect(x, y, right - x, bottom - y);
    }
}
