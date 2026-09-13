package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Hands pointer and keyboard control to the running game while an MCSX workspace stays on screen.
 *
 * <p>Input ownership normally follows the cursor grab: ungrabbed, a mounted workspace owns pointer
 * and keyboard the way a screen does while the game keeps ticking underneath. Requesting focus locks
 * the cursor and vanilla reads its own bindings — {@code KeyboardHandler} refreshes binding state,
 * {@code Minecraft#handleKeybinds} runs, and rebinds are honoured because vanilla, not the UI, is
 * reading them. Logical focus remains held while a screenless game overlay temporarily releases the
 * cursor. Screens opened from gameplay hand it back on close. Releasing gives everything back to the
 * workspace.</p>
 *
 * <p>Escape releases focus rather than dismissing the workspace, so there is always a way back to the
 * UI. Call from any thread; the change is applied on the client thread. Both operations belong to the
 * workspace mounted when they were called: without one they do nothing, and a hand-off still queued
 * when its workspace closes is dropped instead of touching the cursor of whatever came after.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameFocus {

    private GameFocus() {
    }

    /** Locks the cursor into the game. Does nothing when no workspace is mounted. */
    public static void request() {
        OverlayHost.requestGameFocus();
    }

    /** Returns the cursor to the workspace. Does nothing when no workspace is mounted. */
    public static void release() {
        OverlayHost.releaseGameFocus();
    }

    /** Whether the game holds input right now. Readable from any thread; never queued or stale. */
    public static boolean isHeld() {
        return OverlayHost.isGameFocused();
    }
}
