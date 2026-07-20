package fr.lacaleche.glue.mcsx.viewport;

/**
 * The game viewport's rectangle within the real window, in physical pixels, top-left origin.
 * The GL-convention bottom edge is always derived against the <em>current</em> window height
 * ({@link #glY}) rather than stored — a stored baseline goes stale the moment the real window
 * resizes, which is exactly when it matters.
 */
public record ViewportBounds(int x, int top, int width, int height) {

    /** The bottom edge measured from the window's bottom, for {@code glBlitFramebuffer}. */
    public int glY(int screenHeight) {
        return screenHeight - top - height;
    }
}
