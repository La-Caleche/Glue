package fr.lacaleche.glue.mcsx.core.dock;

import java.util.List;

/**
 * A whole dock workspace: the docked split tree plus any floating windows.
 *
 * @param tree the docked tree, or null for an empty workspace (everything floating or closed)
 */
public record DockLayout(DockNode tree, List<DockFloat> floats) {

    public DockLayout {
        floats = List.copyOf(floats);
    }

    public static DockLayout empty() {
        return new DockLayout(null, List.of());
    }

    public DockLayout withTree(DockNode newTree) {
        return new DockLayout(newTree, floats);
    }

    public DockLayout withFloats(List<DockFloat> newFloats) {
        return new DockLayout(tree, newFloats);
    }
}
