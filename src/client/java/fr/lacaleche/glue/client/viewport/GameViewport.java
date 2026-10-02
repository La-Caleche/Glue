package fr.lacaleche.glue.client.viewport;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Objects;

/**
 * Confines the world render to a rectangle of the window so editor chrome can own the rest of it.
 *
 * <p>While bounds are set, the world, its post chains and everything hung off the world pass render
 * into an offscreen target of exactly that size, with the window reporting those dimensions so the
 * projection aspect, GUI scale and any consumer sizing its own buffers from the window all agree. The
 * result is composited back into the real frame at the requested position before the GUI is drawn;
 * the HUD and any open screen are then laid out and rendered inside the same rectangle.</p>
 *
 * <p>Bounds are in framebuffer pixels with a top-left origin, matching {@code Window#getWidth()}. Set
 * them from any thread; they are read once per frame on the render thread.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameViewport {

    private static volatile Bounds bounds;

    private GameViewport() {
    }

    public static void set(Bounds next) {
        bounds = Objects.requireNonNull(next, "bounds");
    }

    /** Returns the world to the whole window. Idempotent. */
    public static void clear() {
        bounds = null;
    }

    public static Bounds bounds() {
        return bounds;
    }

    public static boolean isActive() {
        return bounds != null;
    }

    /** A window rectangle in framebuffer pixels, top-left origin. */
    public record Bounds(int x, int y, int width, int height) {

        public Bounds {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Viewport bounds must be positive: " + width + "x" + height);
            }
        }
    }
}
