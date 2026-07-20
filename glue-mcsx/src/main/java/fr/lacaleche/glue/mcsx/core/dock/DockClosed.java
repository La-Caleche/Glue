package fr.lacaleche.glue.mcsx.core.dock;

import fr.lacaleche.glue.mcsx.core.dock.DropTarget.DropZone;

import java.util.List;

/**
 * Where a closed pane was, captured at close time so reopening restores its place instead of
 * spawning a fresh cascaded window. Persisted with the layout — a closed pane keeps its entry in
 * the layout file rather than vanishing from it.
 *
 * <p>Anchoring is by <b>pane ids only</b>: node ids are never persisted (a loaded layout re-mints
 * them), so a ghost can't point at a leaf or split directly. Each kind records the panes it sat
 * with; {@link DockOps#openPane} re-resolves them against whatever the layout looks like by then,
 * falling back kind by kind and finally to the cascaded window.</p>
 *
 * @param kind        how the pane was placed when it closed
 * @param withPane    {@code TABBED}: a pane that shared the leaf (the nearest surviving neighbour)
 * @param tabIndex    {@code TABBED}: the strip position it held
 * @param besidePanes {@code BESIDE}: every pane of the sibling subtree it was split against
 * @param zone        {@code BESIDE}: which side of that sibling it occupied
 * @param share       {@code BESIDE}: its share of the split
 * @param x           {@code FLOAT}: the window frame it floated in
 */
public record DockClosed(Kind kind, String withPane, int tabIndex,
                         List<String> besidePanes, DropZone zone, double share,
                         int x, int y, int w, int h) {

    /** The placement a closed pane came from, in restore-preference order. */
    public enum Kind { TABBED, BESIDE, ROOT, FLOAT }

    public DockClosed {
        besidePanes = besidePanes == null ? List.of() : List.copyOf(besidePanes);
    }

    public static DockClosed tabbed(String withPane, int tabIndex) {
        return new DockClosed(Kind.TABBED, withPane, tabIndex, List.of(), null, 0, 0, 0, 0, 0);
    }

    public static DockClosed beside(List<String> besidePanes, DropZone zone, double share) {
        return new DockClosed(Kind.BESIDE, null, 0, besidePanes, zone, share, 0, 0, 0, 0);
    }

    /** The pane's leaf was the entire docked tree. */
    public static DockClosed root() {
        return new DockClosed(Kind.ROOT, null, 0, List.of(), null, 0, 0, 0, 0, 0);
    }

    public static DockClosed floating(int x, int y, int w, int h) {
        return new DockClosed(Kind.FLOAT, null, 0, List.of(), null, 0, x, y, w, h);
    }
}
