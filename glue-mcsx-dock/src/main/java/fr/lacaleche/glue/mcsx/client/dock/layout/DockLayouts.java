package fr.lacaleche.glue.mcsx.client.dock.layout;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockLayoutCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Arrays;
import java.util.List;

/** Concise factories for semantic dock layouts and the parser for persisted layout documents. */
@Environment(EnvType.CLIENT)
public final class DockLayouts {

    private DockLayouts() {
    }

    public static DockLayout empty() {
        return new DockLayout(null, List.of());
    }

    public static DockLayout layout(DockNode tree) {
        return new DockLayout(tree, List.of());
    }

    public static DockLayout layout(DockNode tree, List<DockWindow> windows) {
        return new DockLayout(tree, windows);
    }

    /**
     * Parses one persisted layout document — the format of saved user layouts and
     * {@code assets/<namespace>/mcsx/dock/<path>.json} resource defaults — applying the same
     * normalization loading applies: duplicate panes are dropped, degenerate splits flatten and
     * shares renormalize. Panes unknown to a workspace are removed only when that workspace loads
     * the layout, so a well-formed document parses without a pane registry — the entry point for
     * validating a shipped layout resource in an ordinary unit test.
     *
     * @throws DockLayoutException when the document cannot be safely interpreted
     */
    public static DockLayout parse(String json) {
        return DockLayoutCodec.read(json);
    }

    public static DockTabs tabs(String... panes) {
        return tabs(Arrays.asList(panes));
    }

    public static DockTabs tabs(List<String> panes) {
        if (panes == null || panes.isEmpty()) {
            throw new IllegalArgumentException("Dock tabs cannot be empty");
        }
        return new DockTabs(panes, panes.getFirst());
    }

    public static DockTabs tabs(String active, List<String> panes) {
        return new DockTabs(panes, active);
    }

    public static DockSplit split(DockAxis axis, List<DockNode> children, List<Double> shares) {
        return new DockSplit(axis, children, shares);
    }

    public static DockSplit evenSplit(DockAxis axis, DockNode... children) {
        List<DockNode> nodes = Arrays.asList(children);
        if (nodes.size() < 2) throw new IllegalArgumentException("A dock split needs at least two children");

        double share = 1.0 / nodes.size();
        return new DockSplit(axis, nodes, nodes.stream().map(node -> share).toList());
    }

    public static DockSplit row(DockNode... children) {
        return evenSplit(DockAxis.HORIZONTAL, children);
    }

    public static DockSplit column(DockNode... children) {
        return evenSplit(DockAxis.VERTICAL, children);
    }

    public static DockWindow window(DockNode node, int x, int y, int width, int height) {
        return new DockWindow(node, x, y, width, height, 0);
    }
}
