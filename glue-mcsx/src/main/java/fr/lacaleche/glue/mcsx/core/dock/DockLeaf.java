package fr.lacaleche.glue.mcsx.core.dock;

import java.util.List;

/**
 * A leaf of the dock tree: a tab strip over one visible pane.
 *
 * @param tabs   pane ids in strip order; may be empty transiently mid-operation, in which case
 *               {@link DockOps} prunes the leaf away before the layout is returned
 * @param active the pane currently shown, one of {@code tabs} (null only when {@code tabs} is empty)
 */
public record DockLeaf(String id, List<String> tabs, String active) implements DockNode {

    public DockLeaf {
        tabs = List.copyOf(tabs);
        if (active != null && !tabs.contains(active)) {
            throw new IllegalArgumentException("active tab '" + active + "' not in " + tabs);
        }
        if (active == null && !tabs.isEmpty()) {
            throw new IllegalArgumentException("a non-empty leaf needs an active tab");
        }
    }

    public static DockLeaf of(DockIds ids, List<String> tabs) {
        return new DockLeaf(ids.next("leaf"), tabs, tabs.isEmpty() ? null : tabs.getFirst());
    }
}
