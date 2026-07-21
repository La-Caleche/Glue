package fr.lacaleche.glue.mcsx.viewport;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Who owns the player's input while a dockspace is open. The single source of truth is the live
 * cursor-grab state — CraftUI's rule: a grabbed cursor means the game is being played and the
 * dock is inert; an ungrabbed cursor, for <em>any</em> reason (a release into the dock, a vanilla
 * screen opening, focus loss), means the dock is hoverable and clickable. There is no separate
 * captured/released mode to get out of sync with reality.
 *
 * <p>{@link #captureGame()} and {@link #releaseToDock()} therefore only steer the grab: capturing
 * clears the force-unlock and grabs; releasing sets it, which frees the cursor and keeps vanilla
 * from re-grabbing (world clicks, screen closes) until the player clicks back into the viewport.
 * All actual grab/release calls funnel through {@link #onFrame}'s transition edge so no call site
 * races the mouse handler.
 */
public final class ViewportInput {

    /** How the player hands input to the game viewport. */
    public enum Mode {
        /** Only programmatic {@link #captureGame()} enters the game. */
        NONE,
        /** Clicking inside the viewport pane captures; the release key hands back. */
        CLICK,
        /** The game has input only while the capture button is held over the pane. */
        HOLD,
        /** The game starts captured; the release key opens the dock. */
        ALWAYS
    }

    // Written from the game thread (begin/end/key/button hooks) AND the ModernUI UI thread
    // (GameViewportView.onTouchEvent → onViewportPressed), read per-frame on the game thread —
    // volatile is the required happens-before edge. Racy compound updates degrade benignly: the
    // worst case is one ignored press, reconciled by onFrame's transition edge next frame.
    private static volatile Mode mode = Mode.CLICK;
    private static volatile int releaseKey = GLFW.GLFW_KEY_ESCAPE;

    private static volatile boolean engaged;
    private static volatile boolean forceUnlock;
    private static volatile boolean prevForceUnlock;
    private static volatile int holdButton = -1;

    private ViewportInput() {
    }

    /**
     * Arms input routing when a dockspace opens. The caller owns the overlay's interactivity
     * (this class steers only the cursor grab), keeping {@code viewport} free of any {@code mui}
     * dependency.
     */
    public static void begin(Mode inputMode, int releaseKeyCode) {
        mode = inputMode;
        releaseKey = releaseKeyCode;
        engaged = true;
        prevForceUnlock = false;
        if (mode == Mode.ALWAYS) {
            captureGame();
        } else {
            releaseToDock();
        }
    }

    /** Disarms on dockspace close; {@link #onFrame} re-grabs the cursor if the game wants it. */
    public static void end() {
        engaged = false;
        forceUnlock = false;
        holdButton = -1;
    }

    public static Mode mode() {
        return mode;
    }

    public static int releaseKeyCode() {
        return releaseKey;
    }

    /** Live truth: the game has input exactly while the cursor is grabbed. */
    public static boolean isGameCaptured() {
        return engaged && Minecraft.getInstance().mouseHandler.isMouseGrabbed();
    }

    /** True while the cursor is free, whatever freed it — the dock is hoverable then. */
    public static boolean dockOwnsPointer() {
        return engaged && !Minecraft.getInstance().mouseHandler.isMouseGrabbed();
    }

    public static boolean isForceUnlock() {
        return forceUnlock;
    }

    /** Hands input to the game: the cursor re-grabs next frame (unless a screen is open). */
    public static void captureGame() {
        if (!engaged) {
            return;
        }
        forceUnlock = false;
    }

    /** Hands input back to the dock: the cursor is released into the viewport pane and stays free. */
    public static void releaseToDock() {
        if (!engaged) {
            return;
        }
        holdButton = -1;
        forceUnlock = true;
    }

    /** A press landed inside the viewport pane while the cursor was free; capture per the mode. */
    public static void onViewportPressed(int button) {
        // a screen rendering in the pane owns clicks there — they must not flip capture state
        if (!engaged || isGameCaptured() || Minecraft.getInstance().screen != null) {
            return;
        }
        if (mode == Mode.CLICK || mode == Mode.ALWAYS) {
            captureGame();
        } else if (mode == Mode.HOLD) {
            holdButton = button;
            captureGame();
        }
    }

    /** Raw button events while the game is captured — ends a HOLD capture on button-up. */
    public static void onRawMouseButton(int button, int action) {
        if (engaged && isGameCaptured() && mode == Mode.HOLD
                && button == holdButton && action == GLFW.GLFW_RELEASE) {
            releaseToDock();
        }
    }

    /** The release key was pressed while the game is captured. */
    public static boolean onReleaseKey(int key, int action) {
        if (engaged && isGameCaptured() && key == releaseKey && action == GLFW.GLFW_PRESS) {
            releaseToDock();
            return true;
        }
        return false;
    }

    /**
     * Reconciles the cursor once per frame on the force-unlock transition edge. Releasing goes
     * through {@code releaseMouse} (whose mixin re-centers into the pane); re-locking only
     * happens when the game itself would want the cursor, i.e. no screen is open.
     */
    static void onFrame(Minecraft minecraft) {
        if (forceUnlock != prevForceUnlock) {
            if (forceUnlock) {
                minecraft.mouseHandler.releaseMouse();
            } else if (minecraft.screen == null) {
                minecraft.mouseHandler.grabMouse();
            }
        }
        prevForceUnlock = forceUnlock;
    }
}
