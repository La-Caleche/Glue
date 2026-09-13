package fr.lacaleche.glue.mcsx.client.internal;

import fr.lacaleche.mui.internal.UIManager;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewRoot;
import icyllis.modernui.widget.EditText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/**
 * Feeds vanilla's raw input callbacks into the overlay's view root.
 *
 * <p>The host only synthesises pointer events for screens, because that is the only way it ever
 * expected a fragment to be interactive. Keys go through the host's own entry points, which have no
 * such assumption; the pointer is rebuilt here against the same public view-root API the host uses.</p>
 */
@Environment(EnvType.CLIENT)
public final class OverlayInput {

    private static final AtomicLong LAST_KEY_EVENT_TIME = new AtomicLong();
    private static final Map<Long, PendingKey> pendingKeys = new ConcurrentHashMap<>();
    private static final Map<KeyIdentity, KeyStream> keyStreams = new HashMap<>();

    private static int buttonState;
    private static boolean forwardingKey;

    private OverlayInput() {
    }

    static ViewRoot viewRoot() {
        UIManager manager = UIManager.getIfInitialized();
        return manager == null ? null : manager.getDecorView().getViewRoot();
    }

    /**
     * Forgets held-button state. A button pressed while no overlay was mounted must not leave a stale
     * bit behind, because a stale bit makes every later hover synthesize a touch move that no touch
     * stream ever opened.
     */
    static void reset() {
        buttonState = 0;
        pendingKeys.clear();
        keyStreams.clear();
    }

    /** Whether any button stream the overlay opened is still waiting for its release. */
    public static boolean hasHeldButtons() {
        return buttonState != 0;
    }

    /** Whether the overlay opened this button's stream, and must therefore see its release. */
    public static boolean isHeld(int button) {
        return (buttonState & 1 << button) != 0;
    }

    public static void hoverMove() {
        ViewRoot root = viewRoot();
        if (root == null) return;

        long now = Core.timeNanos();
        float x = pointerX();
        float y = pointerY();
        root.enqueueInputEvent(MotionEvent.obtain(now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0));
        if (buttonState != 0) {
            root.enqueueInputEvent(MotionEvent.obtain(now, MotionEvent.ACTION_MOVE, 0, x, y, 0,
                    buttonState, 0));
        }
    }

    public static void scroll(double horizontal, double vertical) {
        ViewRoot root = viewRoot();
        if (root == null) return;

        MotionEvent event = MotionEvent.obtain(Core.timeNanos(), MotionEvent.ACTION_SCROLL,
                pointerX(), pointerY(), 0);
        event.setAxisValue(MotionEvent.AXIS_HSCROLL, (float) horizontal);
        event.setAxisValue(MotionEvent.AXIS_VSCROLL, (float) vertical);
        root.enqueueInputEvent(event);
    }

    public static void mouseButton(int button, int action, int modifiers) {
        ViewRoot root = viewRoot();
        if (root == null) return;

        // Button state is tracked from the events themselves rather than polled from GLFW: the
        // events already arrive in order, and polling would blind this path to injected input, whose
        // presses the OS never saw.
        int actionButton = 1 << button;
        boolean pressed = action == GLFW_PRESS;
        int state = pressed ? buttonState | actionButton : buttonState & ~actionButton;
        buttonState = state;
        long now = Core.timeNanos();
        float x = pointerX();
        float y = pointerY();
        // A touch stream opens on the first button down and closes on the last button up; the
        // in-between presses are button events only. Ordering follows the platform contract, not the
        // screen host: DOWN precedes BUTTON_PRESS and BUTTON_RELEASE precedes UP, because a
        // BUTTON_PRESS delivered before its DOWN re-arms the press state the button event just
        // cleared, which turns a right click into two context clicks.
        boolean streamEdge = pressed ? state == actionButton : state == 0;
        if (pressed && streamEdge) {
            root.enqueueInputEvent(MotionEvent.obtain(now, MotionEvent.ACTION_DOWN, actionButton,
                    x, y, modifiers, state, 0));
        }
        root.enqueueInputEvent(MotionEvent.obtain(now,
                pressed ? MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE,
                actionButton, x, y, modifiers, state, 0));
        if (!pressed && streamEdge) {
            root.enqueueInputEvent(MotionEvent.obtain(now, MotionEvent.ACTION_UP, actionButton,
                    x, y, modifiers, state, 0));
        }
    }

    public static boolean key(int keyCode, int scanCode, int action, int modifiers) {
        ViewRoot root = viewRoot();
        OverlayHost.Mount owner = OverlayHost.inputOwner();
        if (root == null || owner == null) return false;

        KeyIdentity identity = new KeyIdentity(keyCode, scanCode);
        KeyStream stream;
        if (action == GLFW.GLFW_PRESS) {
            stream = new KeyStream(owner);
            keyStreams.put(identity, stream);
        } else {
            stream = keyStreams.get(identity);
            if (stream == null) stream = KeyStream.uiOwned(owner);
            if (action == GLFW.GLFW_RELEASE) keyStreams.remove(identity);
        }

        long eventTime = nextKeyEventTime();
        PendingKey pending = new PendingKey(
                stream,
                new RawKey(owner, keyCode, scanCode, action, modifiers)
        );
        pendingKeys.put(eventTime, pending);
        root.enqueueInputEvent(KeyEvent.obtain(
                eventTime,
                action == GLFW.GLFW_RELEASE ? KeyEvent.ACTION_UP : KeyEvent.ACTION_DOWN,
                keyCode,
                action == GLFW.GLFW_REPEAT ? 1 : 0,
                modifiers,
                scanCode,
                0
        ));
        root.mHandler.post(() -> resolveKey(eventTime, false, null));
        return true;
    }

    /** Called at mui-lite's terminal key seam after no View, shortcut, or focus navigation used it. */
    public static void unhandledKey(KeyEvent event) {
        resolveKey(event.getEventTimeNano(), true, event);
    }

    /** Completes a stream whose release left overlay capture before its asynchronous press resolved. */
    public static boolean releaseOutsideCapture(int keyCode, int scanCode, int modifiers) {
        KeyStream stream = keyStreams.remove(new KeyIdentity(keyCode, scanCode));
        if (stream == null) return false;

        stream.resolve(new RawKey(stream.owner, keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers), false);
        return true;
    }

    public static boolean isForwardingKey() {
        return forwardingKey;
    }

    static void forwardIdleEscape(OverlayHost.Mount owner, int scanCode, int modifiers) {
        forward(new RawKey(owner, GLFW.GLFW_KEY_ESCAPE, scanCode, GLFW.GLFW_PRESS, modifiers));
    }

    public static void charTyped(int codePoint) {
        UIManager manager = UIManager.getIfInitialized();
        if (manager == null) return;

        for (char character : Character.toChars(codePoint)) {
            manager.onCharTyped(character);
        }
    }

    /**
     * The pointer is reported in window pixels and the view root works in framebuffer pixels. The
     * frame's true framebuffer size is used rather than the live one, which a game viewport narrows
     * for part of every frame. Also the conversion {@link OverlayHost} hit-tests with, so the
     * decision space and the delivery space cannot disagree.
     */
    static float pointerX() {
        Minecraft minecraft = Minecraft.getInstance();
        return (float) (minecraft.mouseHandler.xpos() * OverlayHost.frameWidth()
                / minecraft.getWindow().getScreenWidth());
    }

    static float pointerY() {
        Minecraft minecraft = Minecraft.getInstance();
        return (float) (minecraft.mouseHandler.ypos() * OverlayHost.frameHeight()
                / minecraft.getWindow().getScreenHeight());
    }

    private static long nextKeyEventTime() {
        long now = Core.timeNanos();
        return LAST_KEY_EVENT_TIME.updateAndGet(previous -> Math.max(now, previous + 1));
    }

    private static void resolveKey(long eventTime, boolean unhandled, KeyEvent event) {
        PendingKey pending = pendingKeys.remove(eventTime);
        if (pending == null) return;

        boolean forward = unhandled && !textEditorClaims(event);
        pending.stream.resolve(pending.key, forward);
    }

    private static boolean textEditorClaims(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || event.getMappedChar() == 0) {
            return false;
        }

        ViewRoot root = viewRoot();
        if (root == null) return false;
        View focused = root.getView().findFocus();
        return focused instanceof EditText;
    }

    private static void forward(RawKey key) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!OverlayHost.isActive(key.owner)) return;
            if (key.action != GLFW.GLFW_RELEASE && !OverlayHost.capturesKeyboard(key.owner)) return;

            forwardingKey = true;
            try {
                minecraft.keyboardHandler.keyPress(
                        minecraft.getWindow().getWindow(),
                        key.keyCode,
                        key.scanCode,
                        key.action,
                        key.modifiers
                );
            } finally {
                forwardingKey = false;
            }
        });
    }

    private record KeyIdentity(int keyCode, int scanCode) {
    }

    private record RawKey(OverlayHost.Mount owner, int keyCode, int scanCode, int action, int modifiers) {
    }

    private record PendingKey(KeyStream stream, RawKey key) {
    }

    private static final class KeyStream {

        private final OverlayHost.Mount owner;
        private Ownership ownership = Ownership.PENDING;
        private RawKey pendingRelease;

        private KeyStream(OverlayHost.Mount owner) {
            this.owner = owner;
        }

        private static KeyStream uiOwned(OverlayHost.Mount owner) {
            KeyStream stream = new KeyStream(owner);
            stream.ownership = Ownership.UI;
            return stream;
        }

        private synchronized void resolve(RawKey key, boolean unhandled) {
            if (key.action == GLFW.GLFW_PRESS) {
                this.resolvePress(key, unhandled);
                return;
            }
            if (key.action == GLFW.GLFW_REPEAT) {
                if (unhandled && this.ownership == Ownership.GAME) forward(key);
                return;
            }

            if (this.ownership == Ownership.PENDING) {
                this.pendingRelease = key;
            } else if (this.ownership == Ownership.GAME) {
                forward(key);
            }
        }

        private void resolvePress(RawKey key, boolean unhandled) {
            this.ownership = unhandled ? Ownership.GAME : Ownership.UI;
            if (this.ownership == Ownership.GAME) {
                forward(key);
                if (this.pendingRelease != null) forward(this.pendingRelease);
            }
            this.pendingRelease = null;
        }
    }

    private enum Ownership {
        PENDING,
        UI,
        GAME
    }
}
