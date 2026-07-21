/*
 * Modern UI.
 * Copyright (C) 2019-2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package fr.lacaleche.glue.mcsx.mui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.FilterMode;
import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.granite.*;
import icyllis.arc3d.opengl.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.annotation.*;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.core.*;
import icyllis.modernui.fragment.*;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.*;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.lifecycle.*;
import fr.lacaleche.glue.mcsx.mui.b3d.GlTexture_Wrapped;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.*;
import icyllis.modernui.util.DisplayMetrics;
import icyllis.modernui.view.*;
import icyllis.modernui.view.menu.ContextMenuBuilder;
import icyllis.modernui.view.menu.MenuHelper;
import icyllis.modernui.widget.EditText;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryUtil;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

import static fr.lacaleche.glue.mcsx.mui.ModernUIMod.LOGGER;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Manage UI thread and connect Minecraft to Modern UI view system at most bottom level.
 * This class is public only for some hooking methods.
 * <p>
 * Vendored and heavily stripped from ModernUI-MC (LGPL): the text engine,
 * tooltip renderer, blur handler, raw Minecraft draw handlers, config reads,
 * screenshot/zoom/dev-hotkeys, debug dump and the ding sounds were all removed.
 * Only the UI-thread bootstrap, view-root/fragment host, input routing and the
 * Arc3D -> Blaze3D blit drive path remain.
 */
@ApiStatus.Internal
public abstract class UIManager implements LifecycleOwner {

    // the logger marker
    protected static final org.apache.logging.log4j.Marker MARKER =
            org.apache.logging.log4j.MarkerManager.getMarker("UIManager");

    // the global instance, lazily init
    protected static volatile UIManager sInstance;

    protected static final int fragment_container = 0x01020007;

    // minecraft
    protected final Minecraft minecraft = Minecraft.getInstance();

    // minecraft window
    protected final Window mWindow = minecraft.getWindow();

    // the UI thread
    private final Thread mUiThread;
    private volatile Looper mLooper;
    volatile boolean mRunning;

    // the view root impl
    protected volatile ViewRootImpl mRoot;

    // the top-level view of the window
    protected WindowGroup mDecor;
    private FragmentContainerView mFragmentContainerView;

    protected static final boolean DEBUG = false;


    /// Task Handling \\\

    // elapsed time from a screen open in milliseconds, Render thread
    protected long mElapsedTimeMillis;

    // time for drawing, Render thread
    protected long mFrameTimeNanos;


    /// Rendering \\\

    private long mLastPurgeNanos;

    private GlTexture_Wrapped mLayerTexture;
    private GlTextureView mLayerTextureView;


    /// User Interface \\\

    // indicates the current Modern UI screen, updated on main thread
    @Nullable
    protected volatile MuiScreen mScreen;


    /// Overlay \\\

    // the MCSX overlay subsystem (HUD mount/unmount, pointer/keyboard routing, embedded present
    // gating); all overlay state and logic live there, this class only delegates
    private final OverlayRouter mOverlayRouter = new OverlayRouter(this);


    /// Lifecycle \\\

    protected LifecycleRegistry mFragmentLifecycleRegistry;
    private final OnBackPressedDispatcher mOnBackPressedDispatcher =
            new OnBackPressedDispatcher(() -> minecraft.schedule(this::onBackPressed));

    private ViewModelStore mViewModelStore;
    protected volatile FragmentController mFragmentController;


    /// Input Event \\\

    protected int mButtonState;

    private final StringBuilder mCharInputBuffer = new StringBuilder();
    private final Runnable mCommitCharInput = this::commitCharInput;
    private final Runnable mSyntheticHoverMove = () -> onHoverMove(false);

    protected UIManager() {
        mUiThread = new Thread(this::run, "UI thread");
        mUiThread.start();
        // integrated with Minecraft
        AudioManager.getInstance().initialize(/*integrated*/ true);

        mRunning = true;
    }

    @RenderThread
    public static void initializeRenderer() {
        Core.checkRenderThread();
        if (ModernUIMod.sDevelopment || DEBUG) {
            Core.glSetupDebugCallback();
        }
        Objects.requireNonNull(sInstance);
        Core.requireImmediateContext();
        LOGGER.info(MARKER, "UI renderer initialized");
    }

    /**
     * Whether the UI manager singleton has been created. Used by external
     * tick/input hooks (mixins) to guard {@link #getInstance()} during the
     * frames rendered before {@code initialize()} runs.
     */
    public static boolean isInitialized() {
        return sInstance != null;
    }

    @NonNull
    public static UIManager getInstance() {
        if (sInstance == null)
            throw new IllegalStateException("UI manager was never initialized. " +
                    "Please check whether mod loader threw an exception before.");
        return sInstance;
    }

    @UiThread
    private void run() {
        try {
            init();
        } catch (Throwable e) {
            LOGGER.fatal(MARKER, "UI manager failed to initialize", e);
            return;
        }
        while (mRunning) {
            try {
                Looper.loop();
            } catch (Throwable e) {
                LOGGER.error(MARKER, "An error occurred on UI thread", e);
                if (mRunning && ModernUIMod.isDeveloperMode()) {
                    continue;
                } else {
                    minecraft.delayCrashRaw(CrashReport.forThrowable(e, "Exception on UI thread"));
                }
            }
            break;
        }
        mRoot.mSurface = RefCnt.move(mRoot.mSurface);
        Core.requireUiRecordingContext().unref();
        LOGGER.debug(MARKER, "Quited UI thread");
    }

    /**
     * Schedule UI and create views.
     *
     * @param fragment the main fragment
     */
    @MainThread
    protected abstract void open(@NonNull Fragment fragment);

    @MainThread
    void onBackPressed() {
        final MuiScreen screen = mScreen;
        if (screen == null)
            return;
        if (screen.getCallback() != null && !screen.getCallback().shouldClose()) {
            return;
        }
        if (screen.isMenuScreen()) {
            if (minecraft.player != null) {
                minecraft.player.closeContainer();
            }
        } else {
            minecraft.setScreen(screen.getPreviousScreen());
        }
    }

    /**
     * Get elapsed time in UI, update every frame. Internal use only.
     *
     * @return drawing time in milliseconds
     */
    static long getElapsedTime() {
        if (sInstance == null) {
            return Core.timeMillis();
        }
        return sInstance.mElapsedTimeMillis;
    }

    /**
     * Get synced frame time, update every frame
     *
     * @return frame time in nanoseconds
     */
    static long getFrameTimeNanos() {
        if (sInstance == null) {
            return Core.timeNanos();
        }
        return sInstance.mFrameTimeNanos;
    }

    public WindowGroup getDecorView() {
        return mDecor;
    }

    public FragmentController getFragmentController() {
        return mFragmentController;
    }

    public OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return mOnBackPressedDispatcher;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        // STRONG reference "this"
        return mFragmentLifecycleRegistry;
    }

    // Called when open a screen from Modern UI, or back to the screen
    @MainThread
    public void initScreen(@NonNull MuiScreen screen) {
        if (mScreen != screen) {
            if (mScreen != null) {
                LOGGER.warn(MARKER, "You cannot set multiple screens.");
                return;
            }
            mRoot.mHandler.post(this::suppressLayoutTransition);
            mFragmentController.getFragmentManager().beginTransaction()
                    .add(fragment_container, screen.getFragment(), "main")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .setReorderingAllowed(true)
                    .commit();
            mRoot.mHandler.post(this::restoreLayoutTransition);
        }
        mScreen = screen;
        // ensure it's resized
        resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }

    @UiThread
    void suppressLayoutTransition() {
    }

    @UiThread
    void restoreLayoutTransition() {
    }

    @UiThread
    private void init() {
        long startTime = System.nanoTime();
        mLooper = Core.initUiThread();

        mRoot = this.new ViewRootImpl();

        mDecor = new WindowGroup(ModernUI.getInstance());
        mDecor.setWillNotDraw(true);
        mDecor.setId(R.id.content);
        updateLayoutDir(false);

        mFragmentContainerView = new FragmentContainerView(ModernUI.getInstance());
        mFragmentContainerView.setLayoutParams(new WindowManager.LayoutParams());
        mFragmentContainerView.setWillNotDraw(true);
        mFragmentContainerView.setId(fragment_container);
        mDecor.addView(mFragmentContainerView);
        mDecor.setIsRootNamespace(true);

        mRoot.setView(mDecor);
        resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

        mDecor.getViewTreeObserver().addOnScrollChangedListener(this::scheduleHoverMoveForScroll);

        mFragmentLifecycleRegistry = new LifecycleRegistry(this);
        mViewModelStore = new ViewModelStore();
        mFragmentController = FragmentController.createController(this.new HostCallbacks());

        mFragmentController.attachHost(null);

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mFragmentController.dispatchCreate();

        mFragmentController.dispatchActivityCreated();
        mFragmentController.execPendingActions();

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mFragmentController.dispatchStart();

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        mFragmentController.dispatchResume();

        LOGGER.info(MARKER, "UI thread initialized in {}ms", (System.nanoTime() - startTime) / 1000000);
    }

    @UiThread
    private void finish() {
        LOGGER.debug(MARKER, "Quiting UI thread");

        mFragmentController.dispatchStop();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);

        mFragmentController.dispatchDestroy();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        // must delay, some messages are not enqueued
        // currently it is a bit longer than a game tick
        mRoot.mHandler.postDelayed(mLooper::quitSafely, 60);
    }

    private void scheduleHoverMoveForScroll() {
        if (!mRunning) {
            return;
        }
        mRoot.mHandler.removeCallbacks(mSyntheticHoverMove);
        mRoot.mHandler.postDelayed(mSyntheticHoverMove, 60);
    }

    /**
     * From screen
     *
     * @param natural natural or synthetic
     */
    @MainThread
    public void onHoverMove(boolean natural) {
        if (!mRunning || mRoot == null) {
            return;
        }
        final long now = Core.timeNanos();
        float x = pointerX();
        float y = pointerY();
        MotionEvent event = MotionEvent.obtain(now, MotionEvent.ACTION_HOVER_MOVE,
                x, y, 0);
        mRoot.enqueueInputEvent(event);
        if (natural && mButtonState > 0) {
            event = MotionEvent.obtain(now, MotionEvent.ACTION_MOVE, 0, x, y, 0, mButtonState, 0);
            mRoot.enqueueInputEvent(event);
        }
    }

    // Hook method, DO NOT CALL
    public void onScroll(double scrollX, double scrollY) {
        if (!mRunning || mRoot == null) {
            return;
        }
        if (mScreen != null || isOverlayPointerAvailable()) {
            final long now = Core.timeNanos();
            float x = pointerX();
            float y = pointerY();
            int mods = 0;
            if (Screen.hasControlDown()) {
                mods |= KeyEvent.META_CTRL_ON;
            }
            if (Screen.hasShiftDown()) {
                mods |= KeyEvent.META_SHIFT_ON;
            }
            MotionEvent event = MotionEvent.obtain(now, MotionEvent.ACTION_SCROLL,
                    x, y, mods);
            event.setAxisValue(MotionEvent.AXIS_HSCROLL, (float) scrollX);
            event.setAxisValue(MotionEvent.AXIS_VSCROLL, (float) scrollY);
            mRoot.enqueueInputEvent(event);
        }
    }

    public void onPostMouseInput(int button, int action, int mods) {
        if (!mRunning || mRoot == null) {
            return;
        }
        // We should ensure (overlay == null && screen != null)
        // and the screen must be a mui screen — or the HUD is mounted and holds the pointer
        if (minecraft.getOverlay() == null && (mScreen != null || isOverlayPointerAvailable())) {
            final long now = Core.timeNanos();
            float x = pointerX();
            float y = pointerY();
            int buttonState = 0;
            for (int i = 0; i < 5; i++) {
                if (glfwGetMouseButton(mWindow.getWindow(), i) == GLFW_PRESS) {
                    buttonState |= 1 << i;
                }
            }
            mButtonState = buttonState;
            int hoverAction = action == GLFW_PRESS ?
                    MotionEvent.ACTION_BUTTON_PRESS : MotionEvent.ACTION_BUTTON_RELEASE;
            int touchAction = action == GLFW_PRESS ?
                    MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
            int actionButton = 1 << button;
            MotionEvent ev = MotionEvent.obtain(now, hoverAction, actionButton,
                    x, y, mods, buttonState, 0);
            mRoot.enqueueInputEvent(ev);
            if ((touchAction == MotionEvent.ACTION_DOWN && (buttonState ^ actionButton) == 0)
                    || (touchAction == MotionEvent.ACTION_UP && buttonState == 0)) {
                ev = MotionEvent.obtain(now, touchAction, actionButton,
                        x, y, mods, buttonState, 0);
                mRoot.enqueueInputEvent(ev);
            }
        }
    }

    public void onKeyPress(int keyCode, int scanCode, int mods) {
        if (!mRunning || mRoot == null) {
            return;
        }
        KeyEvent keyEvent = KeyEvent.obtain(Core.timeNanos(), KeyEvent.ACTION_DOWN, keyCode, 0,
                mods, scanCode, 0);
        mRoot.enqueueInputEvent(keyEvent);
    }

    public void onKeyRelease(int keyCode, int scanCode, int mods) {
        if (!mRunning || mRoot == null) {
            return;
        }
        KeyEvent keyEvent = KeyEvent.obtain(Core.timeNanos(), KeyEvent.ACTION_UP, keyCode, 0,
                mods, scanCode, 0);
        mRoot.enqueueInputEvent(keyEvent);
    }

    @MainThread
    public boolean onCharTyped(char ch) {
        if (!mRunning) {
            return false;
        }
        // block NUL and DEL character
        if (ch == '\0' || ch == '\u007F') {
            return false;
        }
        mCharInputBuffer.append(ch);
        Core.postOnMainThread(mCommitCharInput);
        return true;
    }

    private void commitCharInput() {
        if (mCharInputBuffer.isEmpty()) {
            return;
        }
        final String input = mCharInputBuffer.toString();
        mCharInputBuffer.setLength(0);
        Message msg = Message.obtain(mRoot.mHandler, () -> {
            if (mDecor.findFocus() instanceof EditText text) {
                final Editable content = text.getText();
                int selStart = text.getSelectionStart();
                int selEnd = text.getSelectionEnd();
                if (selStart >= 0 && selEnd >= 0) {
                    Selection.setSelection(content, Math.max(selStart, selEnd));
                    content.replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd), input);
                }
            }
        });
        msg.setAsynchronous(true);
        msg.sendToTarget();
    }

    @RenderThread
    public void render(@Nullable GuiGraphics gr, int mouseX, int mouseY, float deltaTick) {
        // ModernUI runs its UI on the render thread, so there is no main Looper and
        // Core.postOnMainThread() parks tasks in Core.sMainCalls until flushed. Drain
        // them each frame — without this, posted work (e.g. committing typed text into
        // a focused EditText) never runs.
        Core.flushMainCalls();

        @RawPtr
        ImmediateContext context = Core.requireImmediateContext();

        var frameTask = mRoot.swapFrameTask();
        @SharedPtr
        Recording recording = frameTask.getLeft();
        @SharedPtr
        ImageProxy surface = frameTask.getRight();

        // Outer try/finally guarantees the surface ref is released even if anything
        // below throws; the inner try/finally guarantees the Blaze3D GL-state reset
        // always runs (at the same logical point on the success path) so an exception
        // can't leave MC's GL state corrupted for the rest of the frame.
        try {
            try {
                if (recording != null) {
                    boolean added;
                    try {
                        added = context.addTask(recording);
                    } finally {
                        recording.close();
                    }
                    if (!added) {
                        LOGGER.error("Failed to add draw commands");
                    }
                }

                ((GLDevice) context.getDevice()).flushRenderCalls();

                if (recording != null) {
                    context.submit();
                } else {
                    context.checkForFinishedWork();
                }
            } finally {
                // force changing Blaze3D state
                GL33C.glDisable(GL33C.GL_STENCIL_TEST);
                GlStateManager._disableScissorTest();
                GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
                GlStateManager._enableBlend();
                GL33C.glEnable(GL33C.GL_BLEND);
                GlStateManager._disableDepthTest();
                GL33C.glDisable(GL33C.GL_DEPTH_TEST);
                GlStateManager._depthFunc(GL33C.GL_LEQUAL);
                GL33C.glDepthFunc(GL33C.GL_LEQUAL);
                GlStateManager._depthMask(true);
                GL33C.glDepthMask(true);
                GlStateManager._disableCull();
                fr.lacaleche.glue.mcsx.viewport.BlazeStateSync.resetSamplersBlendTextures();
                // Arc3D's flush ran raw GL past Blaze3D's state caches; any cache left stale makes
                // a later vanilla state change get skipped, corrupting the next queued GUI draws.
                fr.lacaleche.glue.mcsx.viewport.BlazeStateSync.resyncAfterRawGl();
            }

            // While the game is embedded in a dock pane, the GUI pipeline renders at pane size, so
            // neither the surfaces nor the UI layer may go through it — DockPresent draws both over
            // the whole window (at full resolution) after the game's blit instead.
            if (fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding.isActive()) {
                if (surface != null
                        && surface.getImage() instanceof @RawPtr GLTexture layer) {
                    mOverlayRouter.onBlit();
                    fr.lacaleche.glue.mcsx.viewport.DockPresent.submitUiLayer(layer);
                }
                return;
            }

            // Blit externally-rendered surfaces (the VFX preview FBO) BELOW the Modern UI
            // layer. Each ExternalSurfaceView punches a transparent hole over its rect, so the
            // preview shows through there while dialogs/menus painted into the UI stay on top.
            fr.lacaleche.glue.mcsx.surface.ExternalSurfaceHost.getInstance()
                    .composite(gr, mWindow, deltaTick);

            if (surface != null) {
                if (surface.getImage() instanceof @RawPtr GLTexture layer) {
                    // draw off-screen target to Minecraft mainTarget (not the default framebuffer)
                    if (mLayerTexture == null || mLayerTexture.source != layer) {
                        if (mLayerTexture != null) {
                            mLayerTextureView.close();
                            mLayerTexture.close();
                        }
                        layer.ref();
                        mLayerTexture = new GlTexture_Wrapped(layer); // move
                        // using the nearest sampler is performant
                        mLayerTexture.setTextureFilter(FilterMode.NEAREST, /*useMipmaps*/ false);
                        mLayerTextureView = (GlTextureView) MuiModApi.get().getRealGpuDevice()
                                .createTextureView(mLayerTexture);
                    } else {
                        // ensure there's ref before submitting to the GPU
                        mLayerTexture.touch();
                    }
                    mOverlayRouter.onBlit();
                    gr.nextStratum();
                    MuiModApi.get().submitGuiElementRenderState(gr, new BlitRenderState(
                            // render target is always premultiplied
                            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                            TextureSetup.singleTexture(mLayerTextureView),
                            new Matrix3x2f().scale(1.0F / mWindow.getGuiScale()),
                            0, 0, mWindow.getWidth(), mWindow.getHeight(),
                            0.0F, 1.0F, 0.0F, 1.0F,
                            ~0,
                            /*scissorArea*/ null
                    ));
                }
            }
        } finally {
            RefCnt.move(surface);
        }
    }

    /**
     * Called when game window size changed, used to re-layout the window.
     */
    @MainThread
    void resize(int width, int height) {
        if (mRoot != null) {
            mRoot.mHandler.post(() -> mRoot.setFrame(width, height));
        }
    }

    @UiThread
    public void updateLayoutDir(boolean forceRTL) {
        if (mDecor == null) {
            return;
        }
        boolean layoutRtl = forceRTL ||
                TextUtils.getLayoutDirectionFromLocale(ModernUI.getSelectedLocale()) == View.LAYOUT_DIRECTION_RTL;
        mDecor.setLayoutDirection(layoutRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LOCALE);
        mDecor.requestLayout();
    }

    @MainThread
    public void removed(@NonNull Screen target) {
        MuiScreen screen = mScreen;
        if (target != screen) {
            LOGGER.warn(MARKER, "No screen to remove, try to remove {}, but have {}", target, screen);
            return;
        }
        mRoot.mHandler.post(this::suppressLayoutTransition);
        mFragmentController.getFragmentManager().beginTransaction()
                .remove(screen.getFragment())
                .setReorderingAllowed(true)
                .commit();
        mRoot.mHandler.post(this::restoreLayoutTransition);
        mScreen = null;
        glfwSetCursor(mWindow.getWindow(), MemoryUtil.NULL);
    }

    // The overlay subsystem is MCSX-authored and lives in OverlayRouter; these delegations keep
    // every public hook the mixins and MuiModApi call, so the vendored surface is unchanged.

    @MainThread
    public boolean openOverlay(@NonNull Supplier<View> factory) {
        return mOverlayRouter.openOverlay(factory);
    }

    @MainThread
    public void closeOverlay() {
        mOverlayRouter.closeOverlay();
    }

    public String overlayDiagnostics() {
        return mOverlayRouter.overlayDiagnostics();
    }

    public void setOverlayInteractive(boolean interactive) {
        mOverlayRouter.setOverlayInteractive(interactive);
    }

    public boolean overlayOwnsKeyboard() {
        return mOverlayRouter.overlayOwnsKeyboard();
    }

    public boolean isOverlayPointerAvailable() {
        return mOverlayRouter.isOverlayPointerAvailable();
    }

    public boolean isPointerOverOverlay() {
        return mOverlayRouter.isPointerOverOverlay();
    }

    private float pointerX() {
        return mOverlayRouter.pointerX();
    }

    private float pointerY() {
        return mOverlayRouter.pointerY();
    }

    @MainThread
    public boolean interceptMouseButton(int button, int action, int mods) {
        return mOverlayRouter.interceptMouseButton(button, action, mods);
    }

    @RenderThread
    public void renderOverlay(@NonNull GuiRenderState guiRenderState, float deltaTick) {
        mOverlayRouter.renderOverlay(guiRenderState, deltaTick);
    }

    @RenderThread
    public void renderEmbeddedOverlay(float deltaTick) {
        mOverlayRouter.renderEmbeddedOverlay(deltaTick);
    }

    public void onRenderTick(boolean isEnd) {
        if (!isEnd) { // phase=start
            final long lastFrameTime = mFrameTimeNanos;
            mFrameTimeNanos = Core.timeNanos();
            final long deltaMillis = (mFrameTimeNanos - lastFrameTime) / 1000000;
            mElapsedTimeMillis += deltaMillis;
        } else {
            // phase=end, main thread
            var context = Core.requireImmediateContext();
            if (mFrameTimeNanos - mLastPurgeNanos >= 20_000_000_000L) {
                mLastPurgeNanos = mFrameTimeNanos;
                context.performDeferredCleanup(120_000);
            }
            if (mLayerTexture != null) {
                // we can drop the ref after submitting to the GPU
                mLayerTexture.close();
            }
            if (!minecraft.isRunning() && mRunning) {
                mRunning = false;
                mRoot.mHandler.post(this::finish);
                if (mLayerTexture != null) {
                    mLayerTextureView.close();
                    mLayerTextureView = null;
                    mLayerTexture = null;
                }
                // later destroy() will be called
            }
        }
    }

    protected void onClientTick(boolean isEnd) {
    }

    public static void destroy() {
        // see onRenderTick() above
        LOGGER.debug(MARKER, "Quiting Modern UI");
        ImageStore.getInstance().clear();
        System.gc();
        Core.requireImmediateContext().unref();
        if (sInstance != null) {
            AudioManager.getInstance().close();
            try {
                // in case of GLFW is terminated too early
                sInstance.mUiThread.join(1000);
            } catch (InterruptedException e) {
                LOGGER.error(MARKER, "Interrupted while waiting for UI thread to quit", e);
            }
        }
        LOGGER.debug(MARKER, "Quited Modern UI");
    }

    @UiThread
    protected class ViewRootImpl extends ViewRoot {

        private final Rect mGlobalRect = new Rect();

        ContextMenuBuilder mContextMenu;
        MenuHelper mContextMenuHelper;

        GraniteSurface mSurface;
        Recording mLastFrameTask;

        private long mLastPurgeNanos;

        @Override
        protected boolean dispatchTouchEvent(MotionEvent event) {
            if (mScreen != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                View v = mDecor.findFocus();
                if (v instanceof EditText) {
                    v.getGlobalVisibleRect(mGlobalRect);
                    if (!mGlobalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        v.clearFocus();
                    }
                }
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onKeyEvent(KeyEvent event) {
            final MuiScreen screen = mScreen;
            if (screen != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                final boolean back;
                if (screen.getCallback() != null) {
                    back = screen.getCallback().isBackKey(event.getKeyCode(), event);
                } else if (screen.isMenuScreen()) {
                    if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                        back = true;
                    } else {
                        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
                        back = MuiModApi.get().isKeyBindingMatches(minecraft.options.keyInventory, key);
                    }
                } else {
                    back = event.getKeyCode() == KeyEvent.KEY_ESCAPE;
                }
                if (back) {
                    View v = mDecor.findFocus();
                    if (v instanceof EditText) {
                        if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) {
                            v.clearFocus();
                        }
                    } else {
                        mOnBackPressedDispatcher.onBackPressed();
                    }
                }
            }
        }

        @Override
        public void setFrame(int width, int height) {
            super.setFrame(width, height);
            if (width > 0 && height > 0) {
                final DisplayMetrics displayMetrics = ModernUI.getInstance().getResources().getDisplayMetrics();
                final float zRatio = Math.min(width, height)
                        / (450f * displayMetrics.density);
                final float zWeightedAdjustment = (zRatio + 2) / 3f;
                final float lightZ = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DP, 500, displayMetrics
                ) * zWeightedAdjustment;
                final float lightRadius = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DP, 800, displayMetrics
                );

                LightingInfo.setLightGeometry(width / 2f, 0, lightZ, lightRadius);
            }
        }

        @Override
        protected Canvas beginDrawLocked(int width, int height) {
            synchronized (mRenderLock) {
                if (mSurface == null ||
                        mSurface.getWidth() != width ||
                        mSurface.getHeight() != height) {
                    if (width > 0 && height > 0) {
                        mSurface = RefCnt.move(mSurface, GraniteSurface.makeRenderTarget(
                                Core.requireUiRecordingContext(),
                                ImageInfo.make(width, height,
                                        ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL,
                                        ColorSpaces.SRGB),
                                false,
                                Engine.SurfaceOrigin.kUpperLeft,
                                null
                        ));
                    }
                }
                if (mSurface != null && width > 0 && height > 0) {
                    return new ArcCanvas(mSurface.getCanvas());
                }
                return null;
            }
        }

        @Override
        protected void endDrawLocked(@NonNull Canvas canvas) {
            canvas.restoreToCount(1);
            Recording task = Core.requireUiRecordingContext().snap();
            synchronized (mRenderLock) {
                if (mLastFrameTask != null) {
                    mLastFrameTask.close();
                }
                mLastFrameTask = task;
                try {
                    mRenderLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (mLastFrameTask != null) {
                    mLastFrameTask.close();
                }
                mLastFrameTask = null;
            }
            var context = Core.requireUiRecordingContext();
            if (System.nanoTime() - mLastPurgeNanos >= 20_000_000_000L) {
                mLastPurgeNanos = System.nanoTime();
                context.performDeferredCleanup(120_000);
            }
        }

        @RenderThread
        private Pair<@SharedPtr Recording, @SharedPtr ImageProxy> swapFrameTask() {
            @SharedPtr
            Recording recording;
            @SharedPtr
            ImageProxy layer;
            synchronized (mRenderLock) {
                if (mSurface != null) {
                    layer = RefCnt.create(mSurface.getBackingTarget());
                } else {
                    layer = null;
                }
                recording = mLastFrameTask;
                mLastFrameTask = null;
                mRenderLock.notifyAll();
            }
            return Pair.of(recording, layer);
        }

        @Override
        public void playSoundEffect(int effectId) {
            if (effectId == SoundEffectConstants.CLICK) {
                UISounds.playClickSound();
            }
        }

        @Override
        public boolean performHapticFeedback(int effectId, boolean always) {
            return false;
        }

        @MainThread
        protected void applyPointerIcon(int pointerType) {
            long fallback = PointerIcon.getSystemIcon(pointerType).getHandle();
            long handle = fr.lacaleche.glue.mcsx.cursor.Cursors.handleFor(pointerType, fallback);
            minecraft.schedule(() -> glfwSetCursor(mWindow.getWindow(), handle));
        }

        @Override
        public boolean showContextMenuForChild(View originalView, float x, float y) {
            if (mContextMenuHelper != null) {
                mContextMenuHelper.dismiss();
                mContextMenuHelper = null;
            }

            if (mContextMenu == null) {
                mContextMenu = new ContextMenuBuilder(ModernUI.getInstance());
            } else {
                mContextMenu.clearAll();
            }

            final MenuHelper helper;
            final boolean isPopup = !Float.isNaN(x) && !Float.isNaN(y);
            if (isPopup) {
                helper = mContextMenu.showPopup(ModernUI.getInstance(), originalView, x, y);
            } else {
                helper = mContextMenu.showPopup(ModernUI.getInstance(), originalView, 0, 0);
            }

            mContextMenuHelper = helper;
            return helper != null;
        }
    }

    @UiThread
    protected class HostCallbacks extends FragmentHostCallback<Object> implements
            ViewModelStoreOwner,
            OnBackPressedDispatcherOwner {
        HostCallbacks() {
            super(ModernUI.getInstance(), new Handler(Looper.myLooper()));
            assert Core.isOnUiThread();
        }

        @Nullable
        @Override
        public Object onGetHost() {
            // intentionally null
            return null;
        }

        @Nullable
        @Override
        public View onFindViewById(int id) {
            return mDecor.findViewById(id);
        }

        @NonNull
        @Override
        public ViewModelStore getViewModelStore() {
            return mViewModelStore;
        }

        @NonNull
        @Override
        public OnBackPressedDispatcher getOnBackPressedDispatcher() {
            return mOnBackPressedDispatcher;
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return mFragmentLifecycleRegistry;
        }
    }
}
