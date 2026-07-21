package fr.lacaleche.glue.mcsx.core.dock;

/**
 * One node of a dock split tree: either a {@link DockLeaf} holding tabbed panes or a
 * {@link DockSplit} dividing its area among children. Nodes are immutable; every mutation in
 * {@link DockOps} rebuilds the affected spine and returns a new tree.
 */
public sealed interface DockNode permits DockLeaf, DockSplit {

    /** Identity within one layout, minted by {@link DockIds} — stable across size changes only. */
    String id();
}
