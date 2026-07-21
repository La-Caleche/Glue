package fr.lacaleche.glue.mcsx.core.dock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole dock workspace: the docked split tree, any floating windows, and the memory of every
 * closed pane. A closed pane never disappears from the layout — {@link DockOps#closePane} moves it
 * into {@link #closed()} with enough context ({@link DockClosed}) for {@link DockOps#openPane} to
 * put it back where it was, and the codec persists that memory with the rest of the layout.
 *
 * @param tree   the docked tree, or null for an empty workspace (everything floating or closed)
 * @param closed closed panes by id, insertion-ordered so the file stays diff-stable
 */
public record DockLayout(DockNode tree, List<DockFloat> floats, Map<String, DockClosed> closed) {

    public DockLayout {
        floats = List.copyOf(floats);
        closed = Collections.unmodifiableMap(new LinkedHashMap<>(closed));
    }

    public DockLayout(DockNode tree, List<DockFloat> floats) {
        this(tree, floats, Map.of());
    }

    public static DockLayout empty() {
        return new DockLayout(null, List.of(), Map.of());
    }

    public DockLayout withTree(DockNode newTree) {
        return new DockLayout(newTree, floats, closed);
    }

    public DockLayout withFloats(List<DockFloat> newFloats) {
        return new DockLayout(tree, newFloats, closed);
    }

    public DockLayout withClosed(Map<String, DockClosed> newClosed) {
        return new DockLayout(tree, floats, newClosed);
    }
}
