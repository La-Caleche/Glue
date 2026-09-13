package fr.lacaleche.glue.mcsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;

/**
 * The window rectangle a transparent pane keeps open onto the running game.
 *
 * <p>While a vanilla screen is open, pointer events inside this rectangle stay with the game — the
 * screen is laid out there — and the workspace keeps every event outside it, so chat, inventory and
 * menus work inside the viewport while the dock around them stays interactive. With no rectangle
 * published, an open screen owns the pointer everywhere and the workspace is visible but inert
 * underneath it.</p>
 *
 * <p>Publish alongside the render-side viewport from the pane that owns the hole: set the pane's
 * rectangle while it shows, clear when it stops showing. Bounds are in framebuffer pixels with a
 * top-left origin. Set from any thread; read per input event on the client thread.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameSurface {

    private static volatile Bounds bounds;

    private GameSurface() {
    }

    public static void set(Bounds next) {
        bounds = Objects.requireNonNull(next, "bounds");
    }

    /** Returns pointer ownership over the whole window to the workspace. Idempotent. */
    public static void clear() {
        bounds = null;
    }

    public static Bounds bounds() {
        return bounds;
    }

    public static boolean contains(double x, double y) {
        Bounds current = bounds;
        return current != null
                && x >= current.x() && x < current.x() + current.width()
                && y >= current.y() && y < current.y() + current.height();
    }

    /** A window rectangle in framebuffer pixels, top-left origin. */
    public record Bounds(int x, int y, int width, int height) {

        public Bounds {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Surface bounds must be positive: " + width + "x" + height);
            }
        }
    }
}
