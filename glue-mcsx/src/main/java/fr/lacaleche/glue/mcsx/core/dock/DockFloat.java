package fr.lacaleche.glue.mcsx.core.dock;

/**
 * A floating dock window: a detached subtree positioned over the workspace.
 *
 * @param node the window content — a single leaf for a plain detached pane, or a split for a
 *             window that was itself divided
 * @param x    left edge in workspace pixels (top-left origin)
 * @param y    top edge in workspace pixels
 * @param z    stacking order; higher draws on top ({@link DockOps#bringToFront})
 */
public record DockFloat(String id, DockNode node, int x, int y, int w, int h, int z) {

    public DockFloat withFrame(int nx, int ny, int nw, int nh) {
        return new DockFloat(id, node, nx, ny, nw, nh, z);
    }

    public DockFloat withNode(DockNode replacement) {
        return new DockFloat(id, replacement, x, y, w, h, z);
    }

    public DockFloat withZ(int nz) {
        return new DockFloat(id, node, x, y, w, h, nz);
    }
}
