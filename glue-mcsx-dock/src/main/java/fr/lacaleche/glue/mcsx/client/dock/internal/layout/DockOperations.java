package fr.lacaleche.glue.mcsx.client.dock.internal.layout;

import fr.lacaleche.glue.mcsx.client.dock.layout.DockAxis;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockNode;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockSplit;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockWindow;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Pure mutations over immutable semantic dock layouts. */
@Environment(EnvType.CLIENT)
public final class DockOperations {

    private static final double NEW_NODE_SHARE = 0.34;
    private static final double EXISTING_NODE_SHARE = 0.66;

    public static final double MIN_RATIO = 0.07;
    public static final int DEFAULT_FLOAT_WIDTH = 360;
    public static final int DEFAULT_FLOAT_HEIGHT = 260;

    private static final int MENU_FLOAT_WIDTH = 380;
    private static final int MENU_FLOAT_HEIGHT = 280;

    private DockOperations() {
    }

    public static Set<String> openSet(DockLayout layout) {
        Objects.requireNonNull(layout, "layout");
        Set<String> panes = new LinkedHashSet<>();
        collectTabs(layout.tree(), panes);
        for (DockWindow window : layout.windows()) collectTabs(window.node(), panes);
        return Set.copyOf(panes);
    }

    public static DockLayout activate(DockLayout layout, DockTabs tabs, String paneId) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(paneId, "paneId");
        if (!tabs.tabs().contains(paneId) || paneId.equals(tabs.active())) return layout;

        return replace(layout, tabs, node -> new DockTabs(tabs.tabs(), paneId));
    }

    public static DockLayout detach(DockLayout layout, String paneId) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(paneId, "paneId");
        return mapRoots(layout, node -> {
            DockNode stripped = rewriteLeaves(node, tabs -> strip(tabs, paneId));
            return stripped == node ? node : flatten(stripped);
        });
    }

    /** Split shares are weights: only the codec and the even-split factory normalize them to one. */
    public static double shareTotal(DockSplit split) {
        Objects.requireNonNull(split, "split");
        double total = 0.0;
        for (double share : split.shares()) total += share;
        return total;
    }

    public static DockLayout adjustSplit(DockLayout layout, DockSplit target, int index, double firstShare) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(target, "target");
        if (!Double.isFinite(firstShare) || index < 0 || index >= target.shares().size() - 1) return layout;

        double minimum = MIN_RATIO * shareTotal(target);
        double pair = target.shares().get(index) + target.shares().get(index + 1);
        if (pair < minimum * 2.0) return layout;

        double first = Math.clamp(firstShare, minimum, pair - minimum);
        if (first == target.shares().get(index)) return layout;

        double second = pair - first;
        List<Double> shares = new ArrayList<>(target.shares());
        shares.set(index, first);
        shares.set(index + 1, second);
        return replace(layout, target, node -> new DockSplit(target.axis(), target.children(), shares));
    }

    public static DockLayout createFloat(DockLayout layout, DockNode node,
                                         int x, int y, int width, int height) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(node, "node");
        DockLayout normalized = normalizeStacking(layout);
        List<DockWindow> windows = new ArrayList<>(normalized.windows());
        windows.add(new DockWindow(node, x, y, Math.max(1, width), Math.max(1, height), windows.size() + 1));
        return normalized.withWindows(windows);
    }

    public static DockLayout moveFloat(DockLayout layout, DockWindow target, int x, int y) {
        return mapWindow(layout, target,
                window -> window.withFrame(x, y, window.width(), window.height()));
    }

    public static DockLayout resizeFloat(DockLayout layout, DockWindow target,
                                         int x, int y, int width, int height) {
        return mapWindow(layout, target,
                window -> window.withFrame(x, y, Math.max(1, width), Math.max(1, height)));
    }

    public static DockLayout raiseFloat(DockLayout layout, DockWindow target) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(target, "target");
        if (!containsIdentity(layout.windows(), target)) return layout;

        if (layout.windows().getLast() == target && hasDenseStacking(layout.windows())) {
            return layout;
        }

        List<DockWindow> ordered = orderedWindows(layout.windows());
        ordered.removeIf(window -> window == target);
        ordered.add(target);
        return withDenseStacking(layout, ordered);
    }

    public static DockLayout removeFloat(DockLayout layout, DockWindow target) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(target, "target");
        List<DockWindow> windows = new ArrayList<>();
        for (DockWindow window : layout.windows()) {
            if (window != target) windows.add(window);
        }
        if (windows.size() == layout.windows().size()) return layout;
        return normalizeStacking(layout.withWindows(windows));
    }

    public static DockLayout toggleFloat(DockLayout layout, String paneId, int stageWidth, int stageHeight) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(paneId, "paneId");
        DockLayout detached = detach(layout, paneId);
        if (detached != layout) return detached;

        int cascade = layout.windows().size() * 26;
        int x = safeCoordinate((long) stageWidth / 2 - MENU_FLOAT_WIDTH / 2L + cascade);
        int y = safeCoordinate((long) stageHeight / 2 - MENU_FLOAT_HEIGHT / 2L + cascade);
        return createFloat(layout, DockLayouts.tabs(paneId), Math.max(20, x), Math.max(20, y),
                MENU_FLOAT_WIDTH, MENU_FLOAT_HEIGHT);
    }

    public static DockLayout dropTab(DockLayout layout, String paneId, DockTabs source,
                                     DropTarget target, int pointerX, int pointerY) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(paneId, "paneId");
        Objects.requireNonNull(source, "source");
        if (!source.tabs().contains(paneId) || !containsIdentity(layout, source)) return layout;
        if (target != null && target.kind() == DropTarget.Kind.TABS && target.tabs() == source
                && target.zone() != DropTarget.Zone.CENTER && source.tabs().size() == 1) {
            return layout;
        }
        if (target != null && target.kind() == DropTarget.Kind.ROOT
                && target.zone() != DropTarget.Zone.CENTER && layout.tree() == source
                && source.tabs().size() == 1) {
            return layout;
        }

        if (target != null && target.kind() == DropTarget.Kind.TABS && target.tabs() == source
                && target.zone() == DropTarget.Zone.CENTER) {
            List<String> reordered = new ArrayList<>(source.tabs());
            int from = reordered.indexOf(paneId);
            reordered.remove(from);
            reordered.add(insertionPoint(target.index(), from, reordered.size()), paneId);
            if (reordered.equals(source.tabs()) && source.active().equals(paneId)) return layout;
            return replace(layout, source, node -> new DockTabs(reordered, paneId));
        }

        DockLayout detached = detach(layout, paneId);
        DockTabs moved = DockLayouts.tabs(paneId);
        if (target == null) {
            return createDefaultFloat(detached, moved, pointerX, pointerY);
        }
        if (target.kind() == DropTarget.Kind.ROOT) {
            if (detached.tree() == null) return detached.withTree(moved);
            if (target.zone() == DropTarget.Zone.CENTER) return layout;
            return detached.withTree(wrap(detached.tree(), moved, target.zone()));
        }

        DockTabs destination = target.tabs() == source
                ? leafWith(detached, source.tabs().stream().filter(tab -> !tab.equals(paneId)).findFirst().orElse(null))
                : target.tabs();
        if (destination == null || !containsIdentity(detached, destination)) {
            return createDefaultFloat(detached, moved, pointerX, pointerY);
        }
        if (target.zone() == DropTarget.Zone.CENTER) {
            List<String> tabs = new ArrayList<>(destination.tabs());
            tabs.add(insertionPoint(target.index(), -1, tabs.size()), paneId);
            return replace(detached, destination, node -> new DockTabs(tabs, paneId));
        }
        return replace(detached, destination, node -> wrap(node, moved, target.zone()));
    }

    public static DockLayout dropFloat(DockLayout layout, DockWindow window, DropTarget target) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(window, "window");
        if (target == null || !containsIdentity(layout.windows(), window)) {
            return layout;
        }

        DockNode moved = window.node();
        DockLayout removed = removeFloat(layout, window);
        if (target.kind() == DropTarget.Kind.ROOT) {
            if (removed.tree() == null) return removed.withTree(moved);
            if (target.zone() == DropTarget.Zone.CENTER) return layout;
            return removed.withTree(wrap(removed.tree(), moved, target.zone()));
        }
        DockTabs destination = target.tabs();
        if (!containsIdentity(removed, destination)) return layout;

        if (target.zone() == DropTarget.Zone.CENTER) {
            // A split-rooted window has no strip to merge; the guides never advertise this target,
            // and the guard keeps a hand-built one honest. Splits dock through the edge zones below.
            if (!(moved instanceof DockTabs movedTabs)) return layout;

            List<String> tabs = new ArrayList<>(destination.tabs());
            int at = insertionPoint(target.index(), -1, tabs.size());
            for (String pane : movedTabs.tabs()) {
                if (!tabs.contains(pane)) tabs.add(at++, pane);
            }
            return replace(removed, destination, node -> new DockTabs(tabs, movedTabs.active()));
        }
        return replace(removed, destination, node -> wrap(node, moved, target.zone()));
    }

    public static DockLayout sanitize(DockLayout layout, Set<String> knownPanes) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(knownPanes, "knownPanes");
        for (String knownPane : knownPanes) {
            Objects.requireNonNull(knownPane, "knownPanes contains null");
        }

        Set<String> seen = new HashSet<>();
        DockLayout sanitized = mapRoots(layout,
                node -> flatten(rewriteLeaves(node, tabs -> retain(tabs, knownPanes, seen))));
        return normalizeStacking(sanitized);
    }

    public static DockLayout presentOnStage(DockLayout layout, int stageWidth, int stageHeight,
                                            int minimumVisibleWidth, int minimumVisibleHeight) {
        Objects.requireNonNull(layout, "layout");
        if (stageWidth < 0 || stageHeight < 0 || minimumVisibleWidth < 0 || minimumVisibleHeight < 0) {
            throw new IllegalArgumentException("Stage and visibility dimensions cannot be negative");
        }

        int maximumX = Math.max(0, stageWidth - Math.min(stageWidth, minimumVisibleWidth));
        int maximumY = Math.max(0, stageHeight - Math.min(stageHeight, minimumVisibleHeight));
        List<DockWindow> windows = new ArrayList<>(layout.windows().size());
        boolean changed = false;
        for (DockWindow window : layout.windows()) {
            int x = Math.clamp(window.x(), 0, maximumX);
            int y = Math.clamp(window.y(), 0, maximumY);
            changed |= x != window.x() || y != window.y();
            windows.add(x == window.x() && y == window.y()
                    ? window
                    : window.withFrame(x, y, window.width(), window.height()));
        }
        return changed ? layout.withWindows(windows) : layout;
    }

    public static DockSplit wrap(DockNode existing, DockNode added, DropTarget.Zone zone) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(zone, "zone");
        if (zone == DropTarget.Zone.CENTER) throw new IllegalArgumentException("Center drops do not split nodes");

        boolean horizontal = zone == DropTarget.Zone.LEFT || zone == DropTarget.Zone.RIGHT;
        boolean first = zone == DropTarget.Zone.LEFT || zone == DropTarget.Zone.TOP;
        DockAxis axis = horizontal ? DockAxis.HORIZONTAL : DockAxis.VERTICAL;
        List<DockNode> children = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        if (first) {
            appendAlongAxis(added, axis, NEW_NODE_SHARE, children, shares);
            appendAlongAxis(existing, axis, EXISTING_NODE_SHARE, children, shares);
        } else {
            appendAlongAxis(existing, axis, EXISTING_NODE_SHARE, children, shares);
            appendAlongAxis(added, axis, NEW_NODE_SHARE, children, shares);
        }
        return new DockSplit(axis, children, shares);
    }

    private static DockLayout replace(DockLayout layout, DockNode target, UnaryOperator<DockNode> replacement) {
        return mapRoots(layout, node -> replace(node, target, replacement));
    }

    private static DockLayout mapRoots(DockLayout layout, UnaryOperator<DockNode> operation) {
        DockNode tree = operation.apply(layout.tree());
        List<DockWindow> windows = new ArrayList<>(layout.windows().size());
        boolean changed = tree != layout.tree();
        for (DockWindow window : layout.windows()) {
            DockNode node = operation.apply(window.node());
            changed |= node != window.node();
            if (node != null) windows.add(node == window.node() ? window : window.withNode(node));
        }
        return changed ? new DockLayout(tree, windows) : layout;
    }

    private static DockNode replace(DockNode node, DockNode target, UnaryOperator<DockNode> replacement) {
        if (node == null) return null;
        if (node == target) return replacement.apply(node);
        if (!(node instanceof DockSplit split)) return node;

        List<DockNode> children = new ArrayList<>(split.children().size());
        boolean changed = false;
        for (DockNode child : split.children()) {
            DockNode replaced = replace(child, target, replacement);
            changed |= replaced != child;
            children.add(replaced);
        }
        return changed ? new DockSplit(split.axis(), children, split.shares()) : split;
    }

    private static DockTabs strip(DockTabs tabs, String paneId) {
        int removedIndex = tabs.tabs().indexOf(paneId);
        if (removedIndex < 0) return tabs;

        List<String> remaining = new ArrayList<>(tabs.tabs());
        remaining.remove(removedIndex);
        if (remaining.isEmpty()) return null;
        String active = tabs.active();
        if (active.equals(paneId)) active = remaining.get(Math.max(0, removedIndex - 1));
        return new DockTabs(remaining, active);
    }

    private static DockTabs retain(DockTabs tabs, Set<String> knownPanes, Set<String> seen) {
        List<String> retained = new ArrayList<>();
        for (String pane : tabs.tabs()) {
            if (knownPanes.contains(pane) && seen.add(pane)) retained.add(pane);
        }
        if (retained.isEmpty()) return null;
        if (retained.equals(tabs.tabs())) return tabs;
        String active = retained.contains(tabs.active()) ? tabs.active() : retained.getFirst();
        return new DockTabs(retained, active);
    }

    private static DockNode rewriteLeaves(DockNode node, UnaryOperator<DockTabs> operation) {
        if (node == null) return null;
        if (node instanceof DockTabs tabs) return operation.apply(tabs);

        DockSplit split = (DockSplit) node;
        List<DockNode> children = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        boolean changed = false;
        for (int index = 0; index < split.children().size(); index++) {
            DockNode child = rewriteLeaves(split.children().get(index), operation);
            changed |= child != split.children().get(index);
            if (child != null) {
                children.add(child);
                shares.add(split.shares().get(index));
            }
        }
        return rebuildSplit(split, children, shares, changed);
    }

    private static DockNode rebuildSplit(DockSplit original, List<DockNode> children,
                                         List<Double> shares, boolean changed) {
        if (children.isEmpty()) return null;
        if (children.size() == 1) return children.getFirst();
        if (!changed) return original;

        double total = 0.0;
        for (double share : shares) total += share;
        List<Double> normalized;
        if (!Double.isFinite(total) || total <= 0.0) {
            double even = 1.0 / children.size();
            normalized = children.stream().map(child -> even).toList();
        } else {
            double divisor = total;
            normalized = shares.stream().map(share -> share / divisor).toList();
        }
        return new DockSplit(original.axis(), children, normalized);
    }

    private static DockNode flatten(DockNode node) {
        if (!(node instanceof DockSplit split)) return node;

        List<DockNode> children = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        boolean changed = false;
        for (int index = 0; index < split.children().size(); index++) {
            DockNode original = split.children().get(index);
            DockNode child = flatten(original);
            changed |= child != original;
            if (child instanceof DockSplit nested && nested.axis() == split.axis()) {
                appendAlongAxis(nested, split.axis(), split.shares().get(index), children, shares);
                changed = true;
            } else {
                children.add(child);
                shares.add(split.shares().get(index));
            }
        }
        return changed ? new DockSplit(split.axis(), children, shares) : split;
    }

    private static void appendAlongAxis(DockNode node, DockAxis axis, double share,
                                        List<DockNode> children, List<Double> shares) {
        if (!(node instanceof DockSplit split) || split.axis() != axis) {
            children.add(node);
            shares.add(share);
            return;
        }

        double total = shareTotal(split);
        for (int index = 0; index < split.children().size(); index++) {
            children.add(split.children().get(index));
            shares.add(share * split.shares().get(index) / total);
        }
    }

    /**
     * The list position a strip drop lands at, after the dragged tab's own slot has been removed:
     * {@link DropTarget#APPEND} means the end, and an index counted past the removed slot shifts
     * down by one so the drop lands where the pointer showed it.
     */
    private static int insertionPoint(int index, int removedIndex, int size) {
        if (index == DropTarget.APPEND) return size;

        int adjusted = removedIndex >= 0 && removedIndex < index ? index - 1 : index;
        return Math.clamp(adjusted, 0, size);
    }

    private static DockLayout createDefaultFloat(DockLayout layout, DockNode node, int pointerX, int pointerY) {
        return createFloat(layout, node,
                safeCoordinate((long) pointerX - DEFAULT_FLOAT_WIDTH / 2L),
                safeCoordinate((long) pointerY - 16L),
                DEFAULT_FLOAT_WIDTH,
                DEFAULT_FLOAT_HEIGHT);
    }

    private static DockLayout mapWindow(DockLayout layout, DockWindow target,
                                        UnaryOperator<DockWindow> operation) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(target, "target");
        List<DockWindow> windows = new ArrayList<>(layout.windows().size());
        boolean changed = false;
        for (DockWindow window : layout.windows()) {
            DockWindow replacement = window == target ? operation.apply(window) : window;
            if (replacement.equals(window)) replacement = window;
            changed |= replacement != window;
            windows.add(replacement);
        }
        return changed ? layout.withWindows(windows) : layout;
    }

    private static DockLayout normalizeStacking(DockLayout layout) {
        if (hasDenseStacking(layout.windows())) return layout;
        return withDenseStacking(layout, orderedWindows(layout.windows()));
    }

    private static DockLayout withDenseStacking(DockLayout layout, List<DockWindow> ordered) {
        List<DockWindow> normalized = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            DockWindow window = ordered.get(index);
            int order = index + 1;
            normalized.add(window.stackingOrder() == order ? window : window.withStackingOrder(order));
        }
        return layout.withWindows(normalized);
    }

    private static List<DockWindow> orderedWindows(List<DockWindow> windows) {
        List<DockWindow> ordered = new ArrayList<>(windows);
        ordered.sort(Comparator.comparingInt(DockWindow::stackingOrder));
        return ordered;
    }

    private static boolean hasDenseStacking(List<DockWindow> windows) {
        for (int index = 0; index < windows.size(); index++) {
            if (windows.get(index).stackingOrder() != index + 1) return false;
        }
        return true;
    }

    private static boolean containsIdentity(List<DockWindow> windows, DockWindow target) {
        for (DockWindow window : windows) {
            if (window == target) return true;
        }
        return false;
    }

    private static boolean containsIdentity(DockLayout layout, DockNode target) {
        if (containsIdentity(layout.tree(), target)) return true;
        for (DockWindow window : layout.windows()) {
            if (containsIdentity(window.node(), target)) return true;
        }
        return false;
    }

    private static boolean containsIdentity(DockNode node, DockNode target) {
        if (node == null) return false;
        if (node == target) return true;
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                if (containsIdentity(child, target)) return true;
            }
        }
        return false;
    }

    private static DockTabs leafWith(DockLayout layout, String paneId) {
        if (paneId == null) return null;
        DockTabs found = leafWith(layout.tree(), paneId);
        if (found != null) return found;
        for (DockWindow window : layout.windows()) {
            found = leafWith(window.node(), paneId);
            if (found != null) return found;
        }
        return null;
    }

    private static DockTabs leafWith(DockNode node, String paneId) {
        if (node instanceof DockTabs tabs) return tabs.tabs().contains(paneId) ? tabs : null;
        if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) {
                DockTabs found = leafWith(child, paneId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectTabs(DockNode node, Set<String> panes) {
        if (node instanceof DockTabs tabs) {
            panes.addAll(tabs.tabs());
        } else if (node instanceof DockSplit split) {
            for (DockNode child : split.children()) collectTabs(child, panes);
        }
    }

    private static int safeCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
