package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockSplit.Dir;
import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Every mutation of a {@link DockLayout}, as pure functions over immutable trees: each op rebuilds
 * only the spine it touches and returns a new layout, so the caller can hold the previous one for
 * free (drag previews, undo). The semantics — wrap ratios, pruning, drop rules, the single-tab
 * self-drop no-op — mirror the design POC ({@code design-poc/dockspace}), which is the reference
 * for how the workspace must behave.
 */
public final class DockOps {

    /** The smallest share a splitter drag can leave either neighbour. */
    public static final double MIN_RATIO = 0.07;

    /** Default frame for a window spawned by dropping a tab into empty space. */
    private static final int DROP_FLOAT_W = 360;
    private static final int DROP_FLOAT_H = 260;

    /** Default frame for a window opened from the panels menu. */
    private static final int MENU_FLOAT_W = 380;
    private static final int MENU_FLOAT_H = 280;

    private DockOps() {
    }

    /** The node with {@code id}, searched in the docked tree and every floating window, or null. */
    public static DockNode find(DockLayout layout, String id) {
        DockNode hit = find(layout.tree(), id);
        for (DockFloat f : layout.floats()) {
            if (hit != null) {
                break;
            }
            hit = find(f.node(), id);
        }
        return hit;
    }

    public static DockLeaf findLeaf(DockLayout layout, String id) {
        return find(layout, id) instanceof DockLeaf leaf ? leaf : null;
    }

    public static DockFloat findFloat(DockLayout layout, String floatId) {
        for (DockFloat f : layout.floats()) {
            if (f.id().equals(floatId)) {
                return f;
            }
        }
        return null;
    }

    /** Every pane id currently open somewhere in the workspace, docked or floating. */
    public static Set<String> openSet(DockLayout layout) {
        Set<String> open = new HashSet<>();
        collectTabs(layout.tree(), open);
        for (DockFloat f : layout.floats()) {
            collectTabs(f.node(), open);
        }
        return open;
    }

    /** Removes {@code paneId} from wherever it is open and prunes what that empties. */
    public static DockLayout detach(DockLayout layout, String paneId) {
        return mapAll(layout, node -> prune(strip(node, paneId)));
    }

    public static DockLayout setActive(DockLayout layout, String leafId, String paneId) {
        return replace(layout, leafId, node -> node instanceof DockLeaf leaf && leaf.tabs().contains(paneId)
                ? new DockLeaf(leaf.id(), leaf.tabs(), paneId)
                : node);
    }

    /**
     * Sets the share of the child left of the dragged splitter. {@code firstRatio} is the target
     * for {@code sizes[index]} as computed from the drag-start snapshot; both neighbours are
     * clamped to {@link #MIN_RATIO} and their sum is preserved, so the rest of the split is
     * untouched.
     */
    public static DockLayout adjustSplit(DockLayout layout, String splitId, int index, double firstRatio) {
        return replace(layout, splitId, node -> {
            if (!(node instanceof DockSplit split) || index < 0 || index >= split.sizes().size() - 1) {
                return node;
            }
            double pair = split.sizes().get(index) + split.sizes().get(index + 1);
            if (pair < 2 * MIN_RATIO) {
                return node;
            }
            double first = Math.max(MIN_RATIO, Math.min(pair - MIN_RATIO, firstRatio));
            List<Double> sizes = new ArrayList<>(split.sizes());
            sizes.set(index, first);
            sizes.set(index + 1, pair - first);
            return new DockSplit(split.id(), split.dir(), split.children(), sizes);
        });
    }

    public static DockLayout moveFloat(DockLayout layout, String floatId, int x, int y) {
        return mapFloat(layout, floatId, f -> f.withFrame(x, y, f.w(), f.h()));
    }

    public static DockLayout resizeFloat(DockLayout layout, String floatId, int x, int y, int w, int h) {
        return mapFloat(layout, floatId, f -> f.withFrame(x, y, w, h));
    }

    public static DockLayout bringToFront(DockLayout layout, String floatId) {
        DockFloat target = findFloat(layout, floatId);
        if (target == null) {
            return layout;
        }
        int max = maxZ(layout);
        // only a UNIQUE max means already-in-front; two windows tied at max (a hand-edited or
        // stale layout file) must still be able to pass each other
        if (target.z() == max && countAtZ(layout, max) == 1) {
            return layout;
        }
        return mapFloat(layout, floatId, f -> f.withZ(max + 1));
    }

    /** Closes a floating window outright; its panes close with it. */
    public static DockLayout removeFloat(DockLayout layout, String floatId) {
        List<DockFloat> floats = new ArrayList<>(layout.floats());
        floats.removeIf(f -> f.id().equals(floatId));
        return layout.withFloats(floats);
    }

    /**
     * The panels-menu toggle: closes {@code paneId} when it is open anywhere, otherwise opens it
     * as a floating window cascaded from the workspace centre.
     */
    public static DockLayout toggleFloat(DockLayout layout, DockIds ids, String paneId,
                                         int stageW, int stageH) {
        if (openSet(layout).contains(paneId)) {
            return detach(layout, paneId);
        }
        int cascade = layout.floats().size() * 26;
        int x = Math.max(20, stageW / 2 - MENU_FLOAT_W / 2 + cascade);
        int y = Math.max(20, stageH / 2 - MENU_FLOAT_H / 2 + cascade);
        return addFloat(layout, ids, DockLeaf.of(ids, List.of(paneId)), x, y, MENU_FLOAT_W, MENU_FLOAT_H);
    }

    /**
     * Finishes a tab drag. A null {@code drop} means the tab was released over empty space and
     * becomes a floating window at the pointer; dropping a leaf's only tab back onto its own leaf
     * is a no-op rather than a pointless detach/re-add cycle.
     *
     * @param ptrX pointer position in workspace pixels, used only to place a spawned window
     */
    public static DockLayout dropTab(DockLayout layout, DockIds ids, String paneId, String fromLeafId,
                                     DropTarget drop, int ptrX, int ptrY) {
        DockLeaf source = findLeaf(layout, fromLeafId);
        if (drop != null && drop.kind() == DropTarget.Kind.LEAF && drop.leafId().equals(fromLeafId)
                && source != null && source.tabs().size() == 1) {
            return layout;
        }
        DockLayout detached = detach(layout, paneId);
        DockLeaf moved = DockLeaf.of(ids, List.of(paneId));
        if (drop == null) {
            return addFloat(detached, ids, moved,
                    ptrX - DROP_FLOAT_W / 2, ptrY - 16, DROP_FLOAT_W, DROP_FLOAT_H);
        }
        if (drop.kind() == DropTarget.Kind.ROOT) {
            DockNode tree = detached.tree() == null
                    ? moved
                    : wrap(ids, detached.tree(), moved, drop.zone());
            return pruneAll(detached.withTree(tree));
        }
        DockLeaf target = findLeaf(detached, drop.leafId());
        if (target == null) {
            return addFloat(detached, ids, moved,
                    ptrX - DROP_FLOAT_W / 2, ptrY - 16, DROP_FLOAT_W, DROP_FLOAT_H);
        }
        if (drop.zone() == DropZone.CENTER) {
            List<String> tabs = new ArrayList<>(target.tabs());
            tabs.add(paneId);
            return pruneAll(replace(detached, target.id(), node -> new DockLeaf(node.id(), tabs, paneId)));
        }
        return pruneAll(replace(detached, target.id(), node -> wrap(ids, node, moved, drop.zone())));
    }

    /**
     * Finishes a floating-window drag over a drop target, docking the window back into the tree.
     * Only single-leaf windows redock (a floated subtree keeps floating); a center drop merges the
     * window's tabs into the target leaf.
     */
    public static DockLayout dropFloat(DockLayout layout, DockIds ids, String floatId, DropTarget drop) {
        DockFloat window = findFloat(layout, floatId);
        if (window == null || drop == null || !(window.node() instanceof DockLeaf leaf)) {
            return layout;
        }
        DockLayout removed = removeFloat(layout, floatId);
        if (drop.kind() == DropTarget.Kind.ROOT) {
            DockNode tree = removed.tree() == null ? leaf : wrap(ids, removed.tree(), leaf, drop.zone());
            return pruneAll(removed.withTree(tree));
        }
        DockLeaf target = findLeaf(removed, drop.leafId());
        if (target == null) {
            return layout;
        }
        if (drop.zone() == DropZone.CENTER) {
            List<String> tabs = new ArrayList<>(target.tabs());
            tabs.addAll(leaf.tabs());
            return pruneAll(replace(removed, target.id(), node -> new DockLeaf(node.id(), tabs, leaf.active())));
        }
        return pruneAll(replace(removed, target.id(), node -> wrap(ids, node, leaf, drop.zone())));
    }

    /**
     * Drops every tab the pane registry does not know, dedupes tabs open twice, and renormalizes
     * float z-orders to dense unique values — the shield between a stale or hand-edited layout
     * file and the running workspace.
     */
    public static DockLayout sanitize(DockLayout layout, Set<String> knownPanes) {
        Set<String> seen = new HashSet<>();
        return normalizeZ(mapAll(layout, node -> prune(retain(node, knownPanes, seen))));
    }

    /**
     * Clamps every floating window's stored frame to the render clamp ({@code x} keeps at least
     * {@code minVisX} px on stage, {@code y} at least the header row). The stored frame is what
     * drag math starts from, so it must agree with what is rendered — a frame saved on a larger
     * window would otherwise ignore drags until the pointer covers the off-screen overshoot.
     */
    public static DockLayout clampFloats(DockLayout layout, int stageW, int stageH,
                                         int minVisX, int minVisY) {
        boolean changed = false;
        List<DockFloat> floats = new ArrayList<>(layout.floats().size());
        for (DockFloat f : layout.floats()) {
            int x = Math.max(0, Math.min(f.x(), stageW - minVisX));
            int y = Math.max(0, Math.min(f.y(), stageH - minVisY));
            changed |= x != f.x() || y != f.y();
            floats.add(x == f.x() && y == f.y() ? f : f.withFrame(x, y, f.w(), f.h()));
        }
        return changed ? layout.withFloats(floats) : layout;
    }

    /**
     * Splits {@code existing}'s area with {@code added} on the {@code zone} side, the newcomer
     * taking roughly a third — the POC's wrap ratio.
     */
    public static DockSplit wrap(DockIds ids, DockNode existing, DockNode added, DropZone zone) {
        boolean row = zone == DropZone.LEFT || zone == DropZone.RIGHT;
        boolean first = zone == DropZone.LEFT || zone == DropZone.TOP;
        List<DockNode> children = first ? List.of(added, existing) : List.of(existing, added);
        List<Double> sizes = first ? List.of(0.34, 0.66) : List.of(0.66, 0.34);
        return DockSplit.of(ids, row ? Dir.ROW : Dir.COL, children, sizes);
    }

    /**
     * Collapses everything a mutation emptied: leaves with no tabs vanish, a split keeps only its
     * surviving children (renormalizing their shares), and a single-child split is replaced by
     * that child. Untouched subtrees come back identical, not rebuilt.
     */
    public static DockNode prune(DockNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof DockLeaf leaf) {
            return leaf.tabs().isEmpty() ? null : leaf;
        }
        DockSplit split = (DockSplit) node;
        List<DockNode> kids = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < split.children().size(); i++) {
            DockNode kid = prune(split.children().get(i));
            changed |= kid != split.children().get(i);
            if (kid != null) {
                kids.add(kid);
                shares.add(split.sizes().get(i));
            }
        }
        if (kids.isEmpty()) {
            return null;
        }
        if (kids.size() == 1) {
            return kids.getFirst();
        }
        if (!changed) {
            return split;
        }
        double sum = 0d;
        for (double share : shares) {
            sum += share;
        }
        double total = sum <= 0d ? 1d : sum;
        return new DockSplit(split.id(), split.dir(), kids,
                shares.stream().map(v -> v / total).toList());
    }

    private static DockLayout pruneAll(DockLayout layout) {
        return mapAll(layout, DockOps::prune);
    }

    /**
     * Applies {@code op} to the docked tree and to every float's node (tree first, floats in
     * order), preserving identity for untouched nodes and dropping floats the op emptied — the
     * one loop behind {@link #detach}, {@link #sanitize} and {@link #pruneAll}.
     */
    private static DockLayout mapAll(DockLayout layout, UnaryOperator<DockNode> op) {
        DockNode tree = op.apply(layout.tree());
        List<DockFloat> floats = new ArrayList<>();
        for (DockFloat f : layout.floats()) {
            DockNode node = op.apply(f.node());
            if (node != null) {
                floats.add(node == f.node() ? f : f.withNode(node));
            }
        }
        return new DockLayout(tree, floats);
    }

    /**
     * Reassigns float z-orders to dense {@code 1..n}, stable by current z. Two floats can tie
     * (an explicit z colliding with the codec's default) and a tie freezes the stacking order,
     * so every load path must come out collision-free. The floats list comes back z-sorted.
     */
    private static DockLayout normalizeZ(DockLayout layout) {
        List<DockFloat> sorted = new ArrayList<>(layout.floats());
        sorted.sort(java.util.Comparator.comparingInt(DockFloat::z));
        boolean dense = true;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).z() != i + 1) {
                dense = false;
                break;
            }
        }
        if (dense) {
            return layout;
        }
        List<DockFloat> floats = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DockFloat f = sorted.get(i);
            floats.add(f.z() == i + 1 ? f : f.withZ(i + 1));
        }
        return layout.withFloats(floats);
    }

    private static int countAtZ(DockLayout layout, int z) {
        int count = 0;
        for (DockFloat f : layout.floats()) {
            if (f.z() == z) {
                count++;
            }
        }
        return count;
    }

    /** Applies {@code op} to the node with {@code id} wherever it lives, docked or floating. */
    private static DockLayout replace(DockLayout layout, String id, UnaryOperator<DockNode> op) {
        DockNode tree = replace(layout.tree(), id, op);
        List<DockFloat> floats = new ArrayList<>();
        for (DockFloat f : layout.floats()) {
            DockNode node = replace(f.node(), id, op);
            floats.add(node == f.node() ? f : f.withNode(node));
        }
        return new DockLayout(tree, floats);
    }

    private static DockNode replace(DockNode node, String id, UnaryOperator<DockNode> op) {
        if (node == null) {
            return null;
        }
        if (node.id().equals(id)) {
            return op.apply(node);
        }
        if (!(node instanceof DockSplit split)) {
            return node;
        }
        List<DockNode> kids = new ArrayList<>();
        boolean changed = false;
        for (DockNode child : split.children()) {
            DockNode replaced = replace(child, id, op);
            changed |= replaced != child;
            kids.add(replaced);
        }
        return changed ? new DockSplit(split.id(), split.dir(), kids, split.sizes()) : split;
    }

    private static DockNode strip(DockNode node, String paneId) {
        if (node == null) {
            return null;
        }
        if (node instanceof DockLeaf leaf) {
            int index = leaf.tabs().indexOf(paneId);
            if (index < 0) {
                return leaf;
            }
            List<String> tabs = new ArrayList<>(leaf.tabs());
            tabs.remove(index);
            String active = leaf.active();
            if (paneId.equals(active)) {
                active = tabs.isEmpty() ? null : tabs.get(Math.max(0, index - 1));
            }
            return new DockLeaf(leaf.id(), tabs, active);
        }
        DockSplit split = (DockSplit) node;
        List<DockNode> kids = new ArrayList<>();
        boolean changed = false;
        for (DockNode child : split.children()) {
            DockNode stripped = strip(child, paneId);
            changed |= stripped != child;
            kids.add(stripped);
        }
        return changed ? new DockSplit(split.id(), split.dir(), kids, split.sizes()) : split;
    }

    private static DockNode retain(DockNode node, Set<String> known, Set<String> seen) {
        if (node == null) {
            return null;
        }
        if (node instanceof DockLeaf leaf) {
            List<String> tabs = new ArrayList<>();
            for (String tab : leaf.tabs()) {
                if (known.contains(tab) && seen.add(tab)) {
                    tabs.add(tab);
                }
            }
            if (tabs.equals(leaf.tabs())) {
                return leaf;
            }
            String active = tabs.contains(leaf.active()) ? leaf.active()
                    : tabs.isEmpty() ? null : tabs.getFirst();
            return new DockLeaf(leaf.id(), tabs, active);
        }
        DockSplit split = (DockSplit) node;
        List<DockNode> kids = new ArrayList<>();
        boolean changed = false;
        for (DockNode child : split.children()) {
            DockNode kept = retain(child, known, seen);
            changed |= kept != child;
            kids.add(kept);
        }
        return changed ? new DockSplit(split.id(), split.dir(), kids, split.sizes()) : split;
    }

    private static DockLayout addFloat(DockLayout layout, DockIds ids, DockNode node,
                                       int x, int y, int w, int h) {
        List<DockFloat> floats = new ArrayList<>(layout.floats());
        floats.add(new DockFloat(ids.next("float"), node, x, y, w, h, maxZ(layout) + 1));
        return layout.withFloats(floats);
    }

    private static DockLayout mapFloat(DockLayout layout, String floatId, UnaryOperator<DockFloat> op) {
        List<DockFloat> floats = new ArrayList<>();
        for (DockFloat f : layout.floats()) {
            floats.add(f.id().equals(floatId) ? op.apply(f) : f);
        }
        return layout.withFloats(floats);
    }

    private static int maxZ(DockLayout layout) {
        int max = 0;
        for (DockFloat f : layout.floats()) {
            max = Math.max(max, f.z());
        }
        return max;
    }

    private static DockNode find(DockNode node, String id) {
        if (node == null) {
            return null;
        }
        if (node.id().equals(id)) {
            return node;
        }
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                DockNode hit = find(child, id);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static void collectTabs(DockNode node, Set<String> into) {
        if (node instanceof DockLeaf leaf) {
            into.addAll(leaf.tabs());
        } else if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                collectTabs(child, into);
            }
        }
    }
}
