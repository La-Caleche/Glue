package fr.lacaleche.glue.mcsx.core.dock;

import java.util.List;

/**
 * Where a dragged tab or window would land, as resolved by {@link #hitTest}. A {@code ROOT}
 * target splits the whole workspace along one edge (or fills it when empty); a {@code LEAF}
 * target either merges into the leaf's tab strip ({@code CENTER}) or splits the leaf's area.
 *
 * @param leafId the target leaf, null for {@code ROOT} targets
 */
public record DropTarget(Kind kind, String leafId, DropZone zone) {

    public enum Kind { ROOT, LEAF }

    public enum DropZone { CENTER, LEFT, RIGHT, TOP, BOTTOM }

    /** Workspace-edge band that promotes a drop to a root split. */
    public static final int ROOT_EDGE_PX = 28;

    /** The centre window of a leaf, as a fraction of its extent on both axes. */
    public static final double CENTER_MIN = 0.34;
    public static final double CENTER_MAX = 0.66;

    /**
     * One hit-testable leaf, top-most first. The caller flattens its geometry into this list:
     * floating windows' leaves ordered by descending z, then the docked tree's leaves.
     *
     * @param floatId the window this leaf floats in, or null when docked
     */
    public record LeafHit(String leafId, String floatId, DockRect rect) {
    }

    /**
     * Resolves the drop target under the pointer, or null when the drop lands nowhere (which
     * spawns a floating window for tab drags and does nothing for window drags).
     *
     * <p>The stage's edge bands win over everything, so a root split is always reachable even
     * when a leaf fills the workspace. The dragged window itself ({@code ignoreFloatId}) is
     * skipped so its own body never shadows the leaves beneath it.
     */
    public static DropTarget hitTest(int x, int y, DockRect stage, boolean treeEmpty,
                                     List<LeafHit> leavesTopFirst, String ignoreFloatId) {
        boolean inStage = stage.contains(x, y);
        if (treeEmpty) {
            return inStage ? new DropTarget(Kind.ROOT, null, DropZone.CENTER) : null;
        }
        if (inStage) {
            if (x - stage.x() < ROOT_EDGE_PX) {
                return new DropTarget(Kind.ROOT, null, DropZone.LEFT);
            }
            if (stage.right() - x < ROOT_EDGE_PX) {
                return new DropTarget(Kind.ROOT, null, DropZone.RIGHT);
            }
            if (y - stage.y() < ROOT_EDGE_PX) {
                return new DropTarget(Kind.ROOT, null, DropZone.TOP);
            }
            if (stage.bottom() - y < ROOT_EDGE_PX) {
                return new DropTarget(Kind.ROOT, null, DropZone.BOTTOM);
            }
        }
        for (LeafHit hit : leavesTopFirst) {
            if (!hit.rect().contains(x, y)
                    || (hit.floatId() != null && hit.floatId().equals(ignoreFloatId))) {
                continue;
            }
            return new DropTarget(Kind.LEAF, hit.leafId(), zoneWithin(hit.rect(), x, y));
        }
        return null;
    }

    private static DropZone zoneWithin(DockRect rect, int x, int y) {
        double fx = (x - rect.x()) / (double) rect.w();
        double fy = (y - rect.y()) / (double) rect.h();
        if (fx > CENTER_MIN && fx < CENTER_MAX && fy > CENTER_MIN && fy < CENTER_MAX) {
            return DropZone.CENTER;
        }
        DropZone zone = DropZone.LEFT;
        double best = fx;
        if (1 - fx < best) {
            zone = DropZone.RIGHT;
            best = 1 - fx;
        }
        if (fy < best) {
            zone = DropZone.TOP;
            best = fy;
        }
        if (1 - fy < best) {
            zone = DropZone.BOTTOM;
        }
        return zone;
    }
}
