package fr.lacaleche.glue.mcsx.surface;

/**
 * Pointer gestures forwarded from an {@link ExternalSurfaceView}, in view-local pixels.
 * All methods are optional; return {@code true} from a press to begin tracking a drag.
 */
public interface SurfaceGestureListener {

    default boolean onSurfaceScroll(float amount, float x, float y) {
        return false;
    }

    default boolean onSurfaceDown(float x, float y, int button) {
        return false;
    }

    default void onSurfaceMove(float x, float y) {
    }

    default void onSurfaceUp() {
    }

    /** Passive pointer motion over the surface (no button held). */
    default void onSurfaceHover(float x, float y) {
    }

    /** The pointer left the surface. */
    default void onSurfaceHoverExit() {
    }
}
