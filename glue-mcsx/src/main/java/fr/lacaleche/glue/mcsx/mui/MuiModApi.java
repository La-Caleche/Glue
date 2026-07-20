/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.GpuDevice;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.View;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.screens.Screen;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Public APIs for the Ignis Modern UI bridge.
 * <p>
 * Vendored and stripped from ModernUI-MC (LGPL): menu screens, post effects,
 * text/emoji helpers, gui-scale algorithm, picture-in-picture, render-type
 * factory and resource-loader callbacks were removed. Only the screen factory,
 * input-binding query, GPU device accessor and gui blit submission remain.
 * <p>
 * Client only.
 */
public abstract class MuiModApi {

    static final MuiModApi INSTANCE = ServiceLoader.load(MuiModApi.class).findFirst()
            .orElseThrow();

    /**
     * Returns the global API instance.
     */
    public static MuiModApi get() {
        return INSTANCE;
    }

    /**
     * Start the lifecycle of user interface with the fragment and create views.
     * This method must be called from client side main thread.
     *
     * @param fragment the main fragment
     */
    @MainThread
    public static void openScreen(@NonNull Fragment fragment) {
        // an embedded-viewport dockspace owns the present path; a modal MUI screen cannot share
        // the frame with it (the screen would render pane-sized and suppress the overlay)
        if (fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding.isActive()) {
            ModernUIMod.LOGGER.error("Cannot open an MCSX screen while a dockspace is embedding the game.");
            return;
        }
        UIManager.getInstance().open(fragment);
    }

    /**
     * Mount a HUD over the running game rather than a modal screen: the world keeps ticking, the
     * cursor stays grabbed, and the view tree draws above the vanilla GUI. The overlay is inert
     * until {@link #setOverlayInteractive(boolean)} gives it the pointer and keyboard — releasing
     * and re-grabbing the cursor is the caller's business.
     *
     * @param factory builds the overlay view; invoked on the UI thread
     */
    @MainThread
    public static boolean openOverlay(@NonNull Supplier<View> factory) {
        return UIManager.getInstance().openOverlay(factory);
    }

    @MainThread
    public static void closeOverlay() {
        UIManager.getInstance().closeOverlay();
    }

    /**
     * Routes the keyboard, and clicks that land on the overlay, to the view tree instead of the
     * game. Vanilla keybinds are suppressed while this is on, so typing into a field cannot make
     * the player walk.
     */
    public static void setOverlayInteractive(boolean interactive) {
        UIManager.getInstance().setOverlayInteractive(interactive);
    }

    /** A one-line snapshot of the overlay pipeline, for diagnosing a HUD that never appears. */
    public static String overlayDiagnostics() {
        if (!UIManager.isInitialized()) {
            return "uiManager=NOT INITIALIZED";
        }
        return UIManager.getInstance().overlayDiagnostics();
    }

    /**
     * Call {@link #createScreen(Fragment, ScreenCallback, Screen, CharSequence)}
     * with the default callback, no previous screen and title.
     */
    @NonNull
    public final <T extends Screen & MuiScreen> T createScreen(@NonNull Fragment fragment) {
        return createScreen(fragment, null, null, null);
    }

    /**
     * Call {@link #createScreen(Fragment, ScreenCallback, Screen, CharSequence)}
     * with no previous screen and title.
     */
    @NonNull
    public final <T extends Screen & MuiScreen> T createScreen(@NonNull Fragment fragment,
                                                               @Nullable ScreenCallback callback) {
        return createScreen(fragment, callback, null, null);
    }

    /**
     * Call {@link #createScreen(Fragment, ScreenCallback, Screen, CharSequence)}
     * with no title.
     */
    @NonNull
    public final <T extends Screen & MuiScreen> T createScreen(@NonNull Fragment fragment,
                                                               @Nullable ScreenCallback callback,
                                                               @Nullable Screen previousScreen) {
        return createScreen(fragment, callback, previousScreen, null);
    }

    /**
     * Creates a Modern UI screen with the given Fragment instance and optional callback.
     * To start the lifecycle of the fragment, use {@link Minecraft#setScreen(Screen)}.
     *
     * @param fragment       the main fragment
     * @param callback       the callback or null to use defaults
     * @param previousScreen the last screen or null
     * @param title          the title for the virtual window
     */
    @NonNull
    public abstract <T extends Screen & MuiScreen> T createScreen(@NonNull Fragment fragment,
                                                                  @Nullable ScreenCallback callback,
                                                                  @Nullable Screen previousScreen,
                                                                  @Nullable CharSequence title);

    public abstract boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key);

    public abstract GpuDevice getRealGpuDevice();

    public abstract void submitGuiElementRenderState(GuiGraphics graphics, GuiElementRenderState renderState);
}
