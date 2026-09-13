package fr.lacaleche.glue.mcsx.client.internal;

import fr.lacaleche.glue.mcsx.Mcsx;
import fr.lacaleche.glue.mcsx.client.GameSurface;
import fr.lacaleche.glue.mcsx.client.internal.compat.AxiomCompatManager;
import fr.lacaleche.mui.MuiApi;
import fr.lacaleche.mui.OverlayHandle;
import fr.lacaleche.mui.internal.fabric.SimpleScreen;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewRoot;
import icyllis.modernui.widget.EditText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Hosts one MCSX fragment beside the game rather than in front of it.
 *
 * <p>A workspace is not a screen. Opening a {@code Screen} hands Minecraft's whole input pipeline to
 * the GUI: {@code KeyboardHandler} stops refreshing keybind state, {@code Minecraft#handleKeybinds}
 * never runs, and gameplay stops. Mounting the same fragment as an overlay leaves {@code
 * Minecraft#screen} null, so the game ticks and reads its bindings exactly as it always does, and this
 * class decides — per frame, per event — which of the two should see an input.</p>
 *
 * <p>The division follows the cursor grab. Grabbed, the game owns pointer and keyboard exactly as it
 * does without the workspace — bindings, mouselook, everything. Ungrabbed, the workspace owns the
 * pointer and sees keyboard events first; keys its View hierarchy leaves unhandled return to
 * Minecraft's complete keyboard path. A vanilla screen that opened out of gameplay is laid out
 * inside the game surface and keeps the keyboard plus the pointer within that rectangle, while the
 * workspace around it stays interactive. The game itself ticks and renders throughout.</p>
 *
 * <p>Ownership of the single overlay slot is explicit: {@link #mount} returns the {@link Mount} that
 * exclusively owns it, and only that object can unmount it. Focus hand-offs are scoped to the mount
 * that requested them, so nothing queued by a closed workspace can touch the cursor afterwards.</p>
 */
@Environment(EnvType.CLIENT)
public final class OverlayHost {

    private static volatile Mount active;
    private static volatile HudMount hud;
    /** The most recent overlay mui-lite mounted, host-owned or not; see {@link #overlayMounted}. */
    private static volatile OverlayHandle mounted;
    private static volatile int frameWidth;
    private static volatile int frameHeight;

    private OverlayHost() {
    }

    /**
     * Records the frame's true framebuffer size, before anything that narrows the window for a game
     * viewport. The overlay is chrome around such a viewport, so it must be laid out and composited
     * against the whole window no matter what the rest of the frame does to {@code Window}.
     *
     * <p>A window resize or a GUI-scale change reaches the overlay's view root from here and nowhere
     * else, so it must follow whatever holds the slot — a workspace or a managed HUD alike.</p>
     */
    public static void beginFrame(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (width == frameWidth && height == frameHeight) return;

        frameWidth = width;
        frameHeight = height;
        if (isOccupied()) resizeRoot(width, height);
    }

    /**
     * Enforces ownership that can change behind the overlay's back. Axiom's full editor dismisses an
     * interactive overlay even when it was already enabled before the mount. Otherwise, vanilla may
     * re-lock the cursor after a screen closes by a path that has already assigned it to the game;
     * the workspace takes it back here. Polled once per frame.
     */
    public static void enforceRuntimePolicy() {
        if (!isMounted()) return;
        if (AxiomCompatManager.isEditorUiEnabled()) {
            closeActive();
            return;
        }
        if (isGameFocused()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    public static int frameWidth() {
        return frameWidth;
    }

    public static int frameHeight() {
        return frameHeight;
    }

    /**
     * Mounts the fragment and hands back exclusive ownership of the overlay slot. Client thread only,
     * and the thread is checked before anything is mutated, so a rejected call leaves no trace.
     *
     * @param onUnmount run on the client thread inside the close operation, after the overlay stops
     *                  mounting and before the cursor returns to the game; the place to synchronously
     *                  release per-frame world claims.
     */
    public static Mount mount(Fragment fragment, Runnable onUnmount) {
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(onUnmount, "onUnmount");
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("An MCSX overlay mounts on the client thread");
        }
        if (isOccupied()) throw new IllegalStateException("An MCSX overlay is already mounted");

        OverlayHandle handle = MuiApi.mountOverlay(fragment);
        Mount mount = new Mount(handle, onUnmount);
        frameWidth = minecraft.getWindow().getWidth();
        frameHeight = minecraft.getWindow().getHeight();
        OverlayInput.reset();
        // Mounting takes the keyboard, exactly as opening a screen would: a binding held at that
        // moment must not stay logically pressed for as long as the workspace is open.
        KeyMapping.releaseAll();
        active = mount;
        resizeRoot(frameWidth, frameHeight);
        return mount;
    }

    /**
     * Mounts a non-interactive HUD fragment in the same exclusive slot, without any of the input
     * ownership a workspace takes: no keybind release, no cursor policy, no pointer or keyboard
     * capture, and {@link #isMounted()} stays false. It is registered here rather than mounted
     * straight through {@code MuiApi} so the host knows the slot is taken — a HUD that the host
     * cannot see would never follow a window resize, and nothing could ask whether the slot is free.
     * Client thread only.
     */
    public static OverlayHandle mountHud(Fragment fragment) {
        Objects.requireNonNull(fragment, "fragment");
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("An MCSX overlay mounts on the client thread");
        }
        if (isOccupied()) throw new IllegalStateException("An MCSX overlay is already mounted");

        HudMount mounted = new HudMount(MuiApi.mountOverlay(fragment));
        hud = mounted;
        frameWidth = minecraft.getWindow().getWidth();
        frameHeight = minecraft.getWindow().getHeight();
        resizeRoot(frameWidth, frameHeight);
        return mounted;
    }

    /**
     * Whether the exclusive overlay slot is taken &mdash; by a workspace, by a managed HUD, or by an
     * overlay mounted straight through {@code MuiApi} by an application. It is exactly the condition
     * {@link #mount} and {@link #mountHud} reject a further overlay on, so a consumer can ask before
     * mounting instead of catching the rejection.
     */
    public static boolean isOccupied() {
        HudMount mountedHud = hud;
        if (active != null || (mountedHud != null && !mountedHud.isClosed())) return true;

        OverlayHandle handle = mounted;
        if (handle == null) return false;
        if (!handle.isClosed()) return true;

        // Forget a witness whose overlay is gone: it would otherwise pin that fragment for the rest
        // of the session, and the next mount is the only thing that would replace it.
        mounted = null;
        return false;
    }

    /**
     * Records the overlay mui-lite has just mounted, whoever asked for it. mui-lite holds one overlay
     * at a time, so a mount made straight through {@code MuiApi} takes the same slot the host hands
     * out &mdash; and the host has to see it, or {@link #isOccupied()} would report a free slot and
     * send the next mount into a rejection. Called from the mount witness mixin, on the client thread.
     */
    public static void overlayMounted(OverlayHandle handle) {
        mounted = handle;
    }

    /**
     * Handles Escape with the layering an editor expects: a focused text editor gives up focus first,
     * then the workspace may cancel a drag or restore a maximized pane. An otherwise unused Escape is
     * sent back through Minecraft's keyboard handler, where it opens the pause screen without
     * dismissing the workspace. The decision needs UI-thread state, so the original key is consumed
     * while it is resolved.
     */
    public static boolean escape(int scanCode, int modifiers) {
        Mount mount = active;
        if (mount == null || !capturesKeyboard()) return false;

        ViewRoot root = OverlayInput.viewRoot();
        if (root == null) return false;

        BooleanSupplier handler = mount.escapeHandler;
        Minecraft minecraft = Minecraft.getInstance();
        root.mHandler.post(() -> {
            if (!isActive(mount)) return;

            View focused = root.getView().findFocus();
            if (focused instanceof EditText) {
                focused.clearFocus();
                return;
            }
            if (handler != null && handler.getAsBoolean()) return;
            minecraft.execute(() -> {
                if (active == mount) OverlayInput.forwardIdleEscape(mount, scanCode, modifiers);
            });
        });
        return true;
    }

    public static boolean isMounted() {
        Mount mount = active;
        return mount != null && mount.acceptsWork();
    }

    /** Closes the interactive overlay that currently owns the host, if any. */
    public static void closeActive() {
        Mount mount = active;
        if (mount != null) mount.close();
    }

    /**
     * Locks the cursor into the game, for a viewport that wants to fly the player. Scoped to the
     * workspace mounted right now: nothing happens without one, and the queued hand-off is dropped
     * if that workspace is gone by the time the client thread runs it.
     */
    public static void requestGameFocus() {
        Mount mount = active;
        if (mount == null || !mount.acceptsWork()) return;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (active != mount) return;

            // Order matters: the overlay declines vanilla's cursor lock while it owns the pointer.
            mount.gameFocused = true;
            minecraft.mouseHandler.grabMouse();
        });
    }

    /**
     * Returns the cursor to the workspace. Scoped exactly like {@link #requestGameFocus}: a release
     * queued by a workspace that has since closed must not unlock the cursor of ordinary gameplay —
     * or of whatever workspace mounted after it.
     */
    public static void releaseGameFocus() {
        Mount mount = active;
        if (mount == null || !mount.acceptsWork()) return;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (active != mount) return;

            boolean wasFocused = mount.gameFocused;
            mount.gameFocused = false;
            // Returning from the game releases whatever movement keys were held mid-flight; the
            // workspace owns the keyboard again and vanilla would otherwise never see their releases.
            if (wasFocused) KeyMapping.releaseAll();
            minecraft.mouseHandler.releaseMouse();
        });
    }

    public static boolean isGameFocused() {
        Mount mount = active;
        return mount != null && mount.acceptsWork() && mount.gameFocused;
    }

    /**
     * Whether the overlay can receive input at all this event. A grabbed cursor means the game is
     * being played and the workspace stands aside. Logical game focus remains authoritative while a
     * screenless game overlay temporarily releases that cursor. A ModernUI screen hides the overlay
     * outright, and Minecraft's loading overlay owns everything.
     */
    private static boolean routesInput() {
        if (!isMounted()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && isGameFocused()) return false;

        return !minecraft.mouseHandler.isMouseGrabbed()
                && minecraft.getOverlay() == null
                && !(minecraft.screen instanceof SimpleScreen);
    }

    /**
     * Whether vanilla should be denied the pointer this event. Buttons the overlay holds keep their
     * stream to the end, wherever the drag wanders. Otherwise, with no screen open the workspace owns
     * the pointer everywhere; with a vanilla screen open it owns everything outside the game surface
     * — the rectangle the screen is laid out in — and a screen with no surface owns the pointer
     * outright, leaving the workspace visible but inert.
     */
    public static boolean capturesPointer() {
        if (OverlayInput.hasHeldButtons()) return true;
        if (!routesInput()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) return true;
        if (GameSurface.bounds() == null) return false;

        return !GameSurface.contains(OverlayInput.pointerX(), OverlayInput.pointerY());
    }

    /**
     * Whether the workspace receives the keyboard first this event. Any open screen owns the
     * keyboard; an idle workspace arbitrates each key through ModernUI and forwards terminally
     * unhandled streams to Minecraft. {@link fr.lacaleche.glue.mcsx.client.GameFocus} remains the
     * deliberate way to hand every key directly to the game.
     */
    public static boolean capturesKeyboard() {
        return routesInput() && Minecraft.getInstance().screen == null;
    }

    static Mount inputOwner() {
        Mount mount = active;
        return mount != null && mount.acceptsWork() && capturesKeyboard() ? mount : null;
    }

    static boolean isActive(Mount mount) {
        return active == mount && mount.acceptsWork();
    }

    static boolean capturesKeyboard(Mount mount) {
        return isActive(mount) && capturesKeyboard();
    }

    /**
     * Composites whatever MCSX overlay is mounted — a workspace or a managed HUD — during the GUI
     * pass. The call is deliberately unconditional: mui-lite already skips composition when nothing
     * is mounted or a screen owns the frame, so every overlay renders without consumers registering
     * a render hook of their own.
     */
    public static void render(GuiGraphics graphics) {
        MuiApi.renderOverlay(graphics);
    }

    /**
     * Nothing in the host drives an overlay's view root on a window resize — only screens are resized,
     * through their vanilla {@code Screen#resize}. The overlay owns the whole window, so it has to
     * follow it here.
     */
    private static void resizeRoot(int width, int height) {
        ViewRoot root = OverlayInput.viewRoot();
        if (root == null) return;

        root.mHandler.post(() -> root.setFrame(width, height));
    }

    /**
     * Ownership of one mounted HUD. Releasing the slot follows the underlying handle: a close that
     * fails leaves the mount registered, and a handle closed from elsewhere — ModernUI shutting its
     * UI thread down — leaves the slot free for the next mount without clobbering it.
     */
    private static final class HudMount implements OverlayHandle {

        private final OverlayHandle handle;

        private HudMount(OverlayHandle handle) {
            this.handle = handle;
        }

        @Override
        public boolean isClosed() {
            return this.handle.isClosed();
        }

        @Override
        public void close() {
            this.handle.close();
            if (hud == this) hud = null;
        }
    }

    /**
     * Exclusive ownership of one mounted overlay. Only this object can unmount what {@link #mount}
     * mounted: a stale or second-hand close is a no-op, so a failed or already-closed workspace can
     * never tear down a different one.
     *
     * <p>{@link #close} may be called from any thread and is idempotent. The complete teardown —
     * closing the underlying handle, clearing host state, releasing the input hole and restoring the
     * cursor — runs as one client-thread operation, and host state is cleared only after the owning
     * handle has closed successfully.</p>
     */
    public static final class Mount {

        private final OverlayHandle handle;
        private final Runnable onUnmount;
        private final AtomicBoolean closing = new AtomicBoolean();
        private volatile BooleanSupplier escapeHandler;
        private volatile boolean gameFocused;

        private Mount(OverlayHandle handle, Runnable onUnmount) {
            this.handle = handle;
            this.onUnmount = onUnmount;
        }

        /**
         * Registers the layer consulted between a focused text editor and Minecraft's idle-Escape
         * fallback: it runs on the UI thread and returns whether it consumed the key — a dock host
         * uses it to cancel an in-flight drag or restore a maximized pane.
         */
        public void setEscapeHandler(BooleanSupplier handler) {
            if (!this.acceptsWork()) return;

            this.escapeHandler = handler;
        }

        /** Whether this mount still accepts workspace operations and input hand-offs. */
        public boolean acceptsWork() {
            return active == this && !this.closing.get() && !this.handle.isClosed();
        }

        public void close() {
            if (!this.closing.compareAndSet(false, true)) return;

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) this.closeNow(minecraft);
            else minecraft.execute(() -> this.closeNow(minecraft));
        }

        private void closeNow(Minecraft minecraft) {
            if (active != this) return;

            try {
                this.handle.close();
            } catch (RuntimeException failure) {
                this.closing.set(false);
                throw failure;
            }
            OverlayInput.reset();
            KeyMapping.releaseAll();
            // The input hole is meaningless without a workspace around it; with it cleared, a still
            // open vanilla screen owns the whole pointer again.
            GameSurface.clear();
            try {
                this.onUnmount.run();
            } catch (RuntimeException failure) {
                Mcsx.LOGGER.warn("MCSX overlay unmount callback failed", failure);
            }
            try {
                // The overlay's last resolved pointer icon would otherwise outlive it: vanilla never
                // calls glfwSetCursor, so a resize arrow left by a splitter would still show in the
                // next screen.
                GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), MemoryUtil.NULL);
                // Vanilla only re-locks the cursor on its own terms; with the overlay gone, hand it back.
                if (minecraft.screen == null && !AxiomCompatManager.ownsCursor()) {
                    minecraft.mouseHandler.grabMouse();
                }
            } finally {
                this.gameFocused = false;
                active = null;
            }
        }
    }
}
