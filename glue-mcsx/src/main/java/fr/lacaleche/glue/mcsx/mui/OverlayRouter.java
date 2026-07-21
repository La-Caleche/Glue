package fr.lacaleche.glue.mcsx.mui;

import fr.lacaleche.glue.mcsx.viewport.GamePassthrough;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.annotation.UiThread;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.view.WindowManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static fr.lacaleche.glue.mcsx.mui.ModernUIMod.LOGGER;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

/**
 * The MCSX overlay subsystem, extracted from the vendored {@link UIManager} so the vendored file
 * stays a thin delegation layer close to upstream. Owns the mounted HUD's lifecycle, the
 * pointer/keyboard ownership rules between overlay and game, the embedded-viewport pointer
 * mapping, and the render-pump gating that keeps the UI thread's frame loop unblocked.
 *
 * <p>Threading: {@code openOverlay}/{@code closeOverlay}/{@code interceptMouseButton} run on the
 * game main thread; {@link #mOverlay} is written only on the UI thread (by the mount/unmount
 * posts); {@code renderOverlay}/{@code renderEmbeddedOverlay} run on the render thread. The UI
 * thread publishes an immutable {@linkplain HitRegion hit-region snapshot} each drawn frame, so
 * no other thread ever walks the live view tree.
 */
final class OverlayRouter {

    private final UIManager manager;

    // the mounted HUD, a sibling of the fragment container in the decor. Unlike a screen it
    // renders while Minecraft.screen == null, so the game keeps running underneath it.
    // Written ONLY on the UI thread; other threads just read the reference.
    private volatile View mOverlay;

    // set on the main thread the moment an overlay is asked for, and cleared only once the
    // unmount (or a cancelled mount) has actually executed on the UI thread. This is what drives
    // the render pump, and it CANNOT be mOverlay: the UI thread parks in endDrawLocked() until
    // render() consumes its frame, so gating the pump on the mount would deadlock — the mount is
    // queued behind the parked looper.
    private volatile boolean mOverlayRequested;

    // bumped by every open and close on the main thread; a queued mount that lost the race to a
    // close sees a newer generation and declines, instead of attaching an overlay whose render
    // pump has already been switched off (which would park the UI thread forever)
    private volatile int mOverlayGeneration;

    // whether the overlay currently owns keyboard input and clicks that land on it
    private volatile boolean mOverlayInteractive;

    // set while the overlay holds the pointer, so a drag that leaves its bounds still
    // belongs to it and the release is never mistaken for a click on the world
    private boolean mOverlayHoldsPointer;

    // keyboard follows the last click: true after a press landed on the overlay, false after one
    // landed anywhere else. Only consulted while a vanilla screen is open — with none, an
    // interactive overlay owns the keyboard outright.
    private boolean mOverlayKeyboardFocus;

    // the window size the view root was last laid out for. A screen re-resizes the root every
    // time it opens (see UIManager.initScreen), which is the only reason the host gets away with
    // having no window-resize hook; an overlay never opens one, so it has to track the window.
    private int mOverlayWindowWidth;
    private int mOverlayWindowHeight;

    // frames the render thread has actually blitted from the UI thread's surface
    private volatile int mBlittedFrames;

    /**
     * One clickable/passthrough rect of the overlay tree, in view-root coordinates, pre-clipped
     * to its ancestors. The UI thread flattens the tree into a list of these in hit-test answer
     * order; input threads scan the list instead of walking live views.
     */
    private record HitRegion(float left, float top, float right, float bottom,
                             boolean passthrough) {

        boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private volatile List<HitRegion> mHitRegions = List.of();

    private final ViewTreeObserver.OnPreDrawListener mHitRegionPublisher = () -> {
        publishHitRegions();
        return true;
    };

    OverlayRouter(UIManager manager) {
        this.manager = manager;
    }

    /**
     * Mounts a HUD that renders over the running game instead of over a screen.
     * <p>
     * The view is built by {@code factory} on the UI thread and added to the decor beside the
     * fragment container, so it draws above any screen and survives {@code Minecraft.screen}
     * changes. It starts non-interactive: it is visible, but the pointer and keyboard still
     * belong to the game until {@link #setOverlayInteractive(boolean)} is called.
     *
     * @param factory builds the overlay view; invoked on the UI thread
     */
    @MainThread
    boolean openOverlay(@NonNull Supplier<View> factory) {
        if (manager.mRoot == null) {
            throw new IllegalStateException("UI thread is not ready");
        }
        if (mOverlayRequested) {
            LOGGER.warn(UIManager.MARKER, "You cannot mount multiple overlays.");
            return false;
        }
        final int generation = ++mOverlayGeneration;
        // Before the post, not inside it: this starts the render pump, and the pump is what
        // wakes the UI thread so the post can run at all.
        mOverlayRequested = true;
        manager.mRoot.mHandler.post(() -> {
            if (generation != mOverlayGeneration) {
                // closed again before the mount ran; nothing was attached, so nothing can park
                mOverlayRequested = false;
                return;
            }
            // The factory runs here, on the UI thread — so a failure would otherwise unwind
            // into Looper.loop() as a generic "error on UI thread" and leave the caller holding
            // a handle to an overlay that silently never mounted.
            try {
                View view = factory.get();
                view.setLayoutParams(new WindowManager.LayoutParams());
                manager.mDecor.addView(view);
                mOverlay = view;
                manager.mDecor.getViewTreeObserver().addOnPreDrawListener(mHitRegionPublisher);
                LOGGER.debug(UIManager.MARKER, "Overlay mounted");
            } catch (Throwable t) {
                LOGGER.error(UIManager.MARKER, "Failed to mount overlay", t);
            }
        });
        syncOverlayFrame();
        return true;
    }

    /**
     * Unmounts the overlay. Always posts the removal — even when the mount has not executed yet
     * — and clears the pump flag only inside that post, strictly after any pending mount in the
     * same queue: a mount that slips past the generation check is therefore still detached
     * before the pump stops, so the UI thread can never park with the pump off.
     */
    @MainThread
    void closeOverlay() {
        mOverlayInteractive = false;
        mOverlayHoldsPointer = false;
        mOverlayKeyboardFocus = false;
        if (!mOverlayRequested) {
            return;
        }
        ++mOverlayGeneration;
        manager.mRoot.mHandler.post(() -> {
            View view = mOverlay;
            mOverlay = null;
            if (view != null) {
                manager.mDecor.getViewTreeObserver().removeOnPreDrawListener(mHitRegionPublisher);
                manager.mDecor.removeView(view);
            }
            mHitRegions = List.of();
            mOverlayRequested = false;
        });
    }

    /**
     * A one-line snapshot of the overlay pipeline, for diagnosing a HUD that mounts but never
     * appears. Each field is one stage: whether the tree mounted, whether the view root was laid
     * out against a real window frame, whether the tree measured to a non-zero size, and whether
     * the UI thread has actually produced frames for the render thread to blit.
     */
    String overlayDiagnostics() {
        View view = mOverlay;
        return "requested=" + mOverlayRequested
                + " mounted=" + (view != null)
                + " decorChildren=" + (manager.mDecor != null ? manager.mDecor.getChildCount() : -1)
                + " rootFrame=" + mOverlayWindowWidth + "x" + mOverlayWindowHeight
                + " rootSize=" + (view != null ? view.getWidth() + "x" + view.getHeight() : "-")
                + " blits=" + mBlittedFrames
                + " screen=" + (manager.mScreen != null)
                + " interactive=" + mOverlayInteractive;
    }

    /**
     * Gives the overlay the keyboard and any click that lands on it. The caller owns the cursor:
     * Modern UI never grabs or releases the mouse, it only stops routing input to the game.
     */
    void setOverlayInteractive(boolean interactive) {
        mOverlayInteractive = interactive;
        if (!interactive) {
            mOverlayHoldsPointer = false;
            mOverlayKeyboardFocus = false;
        }
    }

    boolean isOverlayInteractive() {
        // minecraft.screen covers vanilla screens (chat, pause, inventory): while one is open it
        // owns the keyboard, otherwise the overlay would keep cancelling its typed input.
        return isOverlayPointerAvailable() && manager.minecraft.screen == null;
    }

    /**
     * Whether typed keys belong to the overlay right now — the gate the keyboard mixin routes by.
     * With no vanilla screen an interactive overlay owns the keyboard outright; with one open
     * (chat, pause) the keyboard follows the pointer, ImGui-style: the overlay gets the keys
     * while the cursor is over it or after the last click landed on it (so a focused text field
     * keeps receiving input when the cursor drifts off), and the screen gets them back the
     * moment the player clicks anywhere else.
     */
    boolean overlayOwnsKeyboard() {
        if (isOverlayInteractive()) {
            return true;
        }
        return isOverlayPointerAvailable()
                && (mOverlayKeyboardFocus || mOverlayHoldsPointer || isPointerOverOverlay());
    }

    /**
     * Pointer-level interactivity, the ImGui rule: the overlay is hoverable, clickable and
     * scrollable whenever the cursor is actually free — including over a vanilla screen, which
     * keeps the keyboard and everything over a {@link GamePassthrough} region. A grabbed cursor
     * cannot point at anything, so it makes the overlay inert without any explicit mode flip.
     */
    boolean isOverlayPointerAvailable() {
        return manager.mRunning && mOverlay != null && mOverlayInteractive
                && manager.mScreen == null && !manager.minecraft.mouseHandler.isMouseGrabbed();
    }

    /**
     * Whether the cursor is over a part of the overlay that can be clicked — used by the host to
     * tell a click on the HUD from a click on the world behind it. A view counts as solid when
     * it paints a background or handles clicks; the binder's bare root containers do not, so the
     * gaps around the HUD read through to the game.
     *
     * <p>Answered from the last published hit-region snapshot (≤ 1 frame stale, and matching
     * what is actually on screen), never from the live tree: the UI thread structurally mutates
     * the tree concurrently with the GLFW input callbacks that call this.
     */
    boolean isPointerOverOverlay() {
        if (mOverlay == null) {
            return false;
        }
        // with a vanilla screen open, game-passthrough regions (the embedded viewport pane)
        // belong to the screen rendering inside them, not to the overlay
        boolean skipPassthrough = manager.minecraft.screen != null;
        float x = pointerX();
        float y = pointerY();
        for (HitRegion region : mHitRegions) {
            if (!region.contains(x, y)) {
                continue;
            }
            if (region.passthrough()) {
                // a passthrough hit wins over any solid ancestor below it in the list — the
                // viewport pane punches its hole through the dock's own opaque background
                if (skipPassthrough) {
                    return false;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    /** Rebuilds the flattened hit-region list; runs on the UI thread before every draw. */
    @UiThread
    private void publishHitRegions() {
        View view = mOverlay;
        if (view == null) {
            mHitRegions = List.of();
            return;
        }
        List<HitRegion> regions = new ArrayList<>();
        collectHitRegions(view, 0f, 0f,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, regions);
        mHitRegions = List.copyOf(regions);
    }

    /**
     * Flattens the subtree into hit regions in exactly the old recursive hit test's answer
     * order: a view's passthrough entry before its children (a passthrough hit beats everything
     * inside it), children topmost-first, the view's own solid entry last. {@code dx/dy} is the
     * origin of the parent's content space in root coordinates; the clip rect is the running
     * intersection of every ancestor's bounds, since a point had to fall inside each ancestor
     * for the recursion to reach a view at all.
     */
    @UiThread
    private static void collectHitRegions(View view, float dx, float dy,
                                          float clipLeft, float clipTop,
                                          float clipRight, float clipBottom,
                                          List<HitRegion> into) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        float left = Math.max(clipLeft, dx + view.getLeft());
        float top = Math.max(clipTop, dy + view.getTop());
        float right = Math.min(clipRight, dx + view.getRight());
        float bottom = Math.min(clipBottom, dy + view.getBottom());
        if (left >= right || top >= bottom) {
            return;
        }
        if (view instanceof GamePassthrough) {
            into.add(new HitRegion(left, top, right, bottom, true));
        }
        if (view instanceof ViewGroup group) {
            float childDx = dx + view.getLeft() - group.getScrollX();
            float childDy = dy + view.getTop() - group.getScrollY();
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                collectHitRegions(group.getChildAt(i), childDx, childDy,
                        left, top, right, bottom, into);
            }
        }
        if (view.getBackground() != null || view.isClickable()) {
            into.add(new HitRegion(left, top, right, bottom, false));
        }
    }

    /**
     * Routes a raw mouse button to the overlay when the overlay owns the pointer.
     *
     * @return true when the event was consumed, meaning vanilla must not see it — no mouse
     *         re-grab, no attack/use swing behind the HUD
     */
    @MainThread
    boolean interceptMouseButton(int button, int action, int mods) {
        if (!isOverlayPointerAvailable() || manager.minecraft.getOverlay() != null) {
            mOverlayHoldsPointer = false;
            if (action == GLFW_PRESS) {
                mOverlayKeyboardFocus = false;
            }
            return false;
        }
        if (action == GLFW_PRESS) {
            if (!mOverlayHoldsPointer && !isPointerOverOverlay()) {
                mOverlayKeyboardFocus = false;
                return false;
            }
            mOverlayHoldsPointer = true;
            mOverlayKeyboardFocus = true;
        } else if (!mOverlayHoldsPointer) {
            return false;
        }
        manager.onPostMouseInput(button, action, mods);
        if (manager.mButtonState == 0) {
            mOverlayHoldsPointer = false;
        }
        return true;
    }

    /**
     * Lays the view root out for the current window. {@code UIManager.initScreen} does this for
     * screens; an overlay has to be given the same treatment on mount and on every window
     * resize, or it is measured against a stale frame and draws nothing.
     */
    void syncOverlayFrame() {
        // while the game is embedded, getWidth/getHeight are pinned to the viewport pane; the UI
        // root must keep the real window's frame or the dock chrome would render at pane size
        boolean embedded = ViewportEmbedding.isActive();
        int width = embedded ? manager.mWindow.getScreenWidth() : manager.mWindow.getWidth();
        int height = embedded ? manager.mWindow.getScreenHeight() : manager.mWindow.getHeight();
        if (width != mOverlayWindowWidth || height != mOverlayWindowHeight) {
            mOverlayWindowWidth = width;
            mOverlayWindowHeight = height;
            manager.resize(width, height);
        }
    }

    /**
     * Draws the mounted overlay when no screen is up. {@code UIManager.render} is the only thing
     * that unblocks the UI thread's frame, so once an overlay is mounted this must run every
     * frame.
     */
    @RenderThread
    void renderOverlay(@NonNull GuiRenderState guiRenderState, float deltaTick) {
        if (manager.minecraft.isRunning() && manager.mRunning && mOverlayRequested
                && manager.mScreen == null && manager.minecraft.getOverlay() == null
                && !ViewportEmbedding.isActive()) {
            syncOverlayFrame();
            manager.render(new GuiGraphics(manager.minecraft, guiRenderState), 0, 0, deltaTick);
        }
    }

    /** Flushes the dock layer after Minecraft has finished drawing and presenting its HUD. */
    @RenderThread
    void renderEmbeddedOverlay(float deltaTick) {
        if (manager.minecraft.isRunning() && manager.mRunning && mOverlayRequested
                && manager.mScreen == null && manager.minecraft.getOverlay() == null
                && ViewportEmbedding.isActive()) {
            syncOverlayFrame();
            manager.render(null, 0, 0, deltaTick);
        }
    }

    /** The render thread blitted one UI frame; feeds the diagnostics counter. */
    @RenderThread
    void onBlit() {
        mBlittedFrames++;
    }

    /**
     * The cursor in view-root coordinates. While the game is embedded, {@code xpos/ypos} hold
     * remapped virtual-viewport coordinates and {@code getWidth()} is pinned to the pane, so
     * both halves of the usual conversion are wrong — the raw window position captured before
     * the remap is already in root-frame pixels.
     */
    float pointerX() {
        if (ViewportEmbedding.isActive()) {
            return (float) ViewportEmbedding.rawX();
        }
        return (float) (manager.minecraft.mouseHandler.xpos()
                * manager.mWindow.getWidth() / manager.mWindow.getScreenWidth());
    }

    float pointerY() {
        if (ViewportEmbedding.isActive()) {
            return (float) ViewportEmbedding.rawY();
        }
        return (float) (manager.minecraft.mouseHandler.ypos()
                * manager.mWindow.getHeight() / manager.mWindow.getScreenHeight());
    }
}
