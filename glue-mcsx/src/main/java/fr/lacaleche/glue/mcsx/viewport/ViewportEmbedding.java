package fr.lacaleche.glue.mcsx.viewport;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * The state hub for embedding the running game inside a dock pane. While bounds are published,
 * Minecraft's framebuffer is pinned to the pane size (the game renders complete — world, HUD,
 * vanilla screens — at pane resolution) and the final present is redirected into the pane's
 * sub-rectangle of the real window; the Modern UI layer is then drawn over the whole window at
 * full resolution by {@link DockPresent}. The approach is CraftUI's, ported from ImGui.
 *
 * <p>Two rectangles are deliberately kept apart. {@link #bounds()} — <em>where</em> the game is
 * presented — follows the pane every frame, so the blit never disagrees with the hole the dock UI
 * punches. The <em>resolution</em> the game renders at ({@link #pinnedWidth()}) follows once per frame
 * in {@link #beginFrame}, keeping the rendered size and presented rectangle consistent.
 *
 * <p>Threading: the dock view publishes bounds from the UI thread; everything else — applying a
 * resize, the mixin reads, the cursor math — happens on the render thread. {@link #beginFrame}
 * is the only place the window is actually resized, so the game never sees a size change
 * mid-frame.
 */
public final class ViewportEmbedding {

    private static volatile boolean active;
    private static volatile ViewportBounds pending;

    // render-thread state. `applied` is the once-per-frame snapshot of `pending` taken in
    // beginFrame; every consumer (framebuffer pin, blit rect, cursor remap) reads it, never
    // `pending` directly — the UI thread publishes mid-frame (input dispatch runs between
    // beginFrame and the render), and a rect newer than the frame's resolution is exactly a
    // one-frame HUD glitch.
    private static ViewportBounds applied;
    private static int pinnedWidth;
    private static int pinnedHeight;

    // the cursor position in real window coordinates, captured before the onMove remap; this is
    // what the dock UI hit-tests against while MouseHandler.xpos holds virtual pane coordinates
    private static volatile double rawX;
    private static volatile double rawY;

    private ViewportEmbedding() {
    }

    /** Routes the UI present through {@link DockPresent}; pinning starts when bounds arrive. */
    public static void activate() {
        active = true;
    }

    /** Stops embedding; the next {@link #beginFrame} restores the window's real size. */
    public static void deactivate() {
        active = false;
        pending = null;
    }

    public static boolean isActive() {
        return active;
    }

    /** The pane rect this frame renders and presents into, or null when presenting normally. */
    public static ViewportBounds bounds() {
        return applied;
    }

    /** Redirects only once the framebuffer is actually pinned, so rect and resolution agree. */
    public static boolean shouldRedirectPresent() {
        return applied != null && pinnedWidth > 0;
    }

    /** The framebuffer width the embedding pinned, 0 when unpinned — the OS resize callback must preserve it. */
    public static int pinnedWidth() {
        return pinnedWidth;
    }

    public static int pinnedHeight() {
        return pinnedHeight;
    }

    /**
     * Publishes the viewport pane's rect in UI window coordinates (top-left origin). Degenerate
     * rects are ignored, not applied: a transient zero-size layout pass mid-mutation must never
     * unpin the game for a frame. Unpinning is an explicit act ({@link #clearPaneBounds}).
     */
    public static void setPaneBounds(int x, int top, int width, int height) {
        if (!active || width < 16 || height < 16) {
            return;
        }
        // published from onDraw every frame; skip the allocation and volatile store when the
        // pane hasn't moved (every frame outside an active drag)
        ViewportBounds current = pending;
        if (current != null && current.x() == x && current.top() == top
                && current.width() == width && current.height() == height) {
            return;
        }
        pending = new ViewportBounds(x, top, width, height);
    }

    /** The workspace no longer has a viewport pane; present normally under the dock UI. */
    public static void clearPaneBounds() {
        pending = null;
    }

    /**
     * Applies pane-size changes at the top of the frame — resizing the window here (and only
     * here) means every consumer of the framebuffer size sees one consistent value per frame.
     * Size changes apply <em>immediately, every frame</em>, exactly like CraftUI: any deferral
     * (an earlier build throttled to 100ms) leaves frames where the game rendered at one size
     * and is shown at another, which reads as the HUD glitching mid-drag. Recreating the render
     * targets per drag-frame is the cheaper artifact — CraftUI ships that and it holds up. A
     * pane that moved without resizing still costs nothing (only the size is compared).
     */
    public static void beginFrame(Minecraft minecraft) {
        ViewportInput.onFrame(minecraft);
        applied = active ? pending : null;
        int targetW = applied != null ? applied.width() : 0;
        int targetH = applied != null ? applied.height() : 0;
        if (targetW == pinnedWidth && targetH == pinnedHeight) {
            return;
        }
        pinnedWidth = targetW;
        pinnedHeight = targetH;
        Window window = minecraft.getWindow();
        if (targetW != 0) {
            window.setWidth(targetW);
            window.setHeight(targetH);
        } else {
            window.setWidth(window.getScreenWidth());
            window.setHeight(window.getScreenHeight());
        }
        minecraft.resizeDisplay();
    }

    /**
     * Maps a real-window cursor position into the virtual viewport, so vanilla — which believes
     * the framebuffer is pane-sized — sees coordinates in its own space. Mapping against the live
     * rect stays correct even while a throttled resize has the frame stretching: vanilla scales
     * by framebuffer/screen afterwards, which cancels the screen term.
     */
    public static double remapX(double x) {
        ViewportBounds b = bounds();
        if (b == null) {
            return x;
        }
        Window window = Minecraft.getInstance().getWindow();
        return (x - b.x()) * window.getScreenWidth() / (double) b.width();
    }

    public static double remapY(double y) {
        ViewportBounds b = bounds();
        if (b == null) {
            return y;
        }
        Window window = Minecraft.getInstance().getWindow();
        return (y - b.top()) * window.getScreenHeight() / (double) b.height();
    }

    public static void setRawCursor(double x, double y) {
        rawX = x;
        rawY = y;
    }

    public static double rawX() {
        return rawX;
    }

    public static double rawY() {
        return rawY;
    }
}
