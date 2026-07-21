/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
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

package fr.lacaleche.glue.mcsx.mui.fabric;

import fr.lacaleche.glue.mcsx.mui.UIManager;
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.jetbrains.annotations.ApiStatus;

import icyllis.modernui.annotation.NonNull;

import static fr.lacaleche.glue.mcsx.mui.ModernUIMod.LOGGER;

/**
 * Fabric host for the Ignis Modern UI bridge.
 * <p>
 * Vendored and stripped from ModernUI-MC (LGPL): the {@code CenterFragment2}
 * dev shortcut, config-driven window mode and the custom render-tick events
 * (which lived in the un-vendored ModernUIFabricClient) were removed. The
 * render-tick driver ({@link #onRenderTick}) therefore needs external wiring;
 * only the client-tick events are hooked here.
 */
@ApiStatus.Internal
public final class UIManagerFabric extends UIManager {

    private UIManagerFabric() {
        super();

        ClientTickEvents.START_CLIENT_TICK.register((mc) -> super.onClientTick(false));
        ClientTickEvents.END_CLIENT_TICK.register((mc) -> super.onClientTick(true));
        ClientLifecycleEvents.CLIENT_STOPPING.register((mc) -> UIManager.destroy());
    }

    @RenderThread
    public static void initialize() {
        Core.checkRenderThread();
        assert sInstance == null;
        sInstance = new UIManagerFabric();
        LOGGER.info(MARKER, "UI manager initialized");
    }

    /**
     * Schedule UI and create views.
     *
     * @param fragment the main fragment
     */
    @MainThread
    protected void open(@NonNull Fragment fragment) {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Not called from main thread");
        }
        minecraft.setScreen(new SimpleScreen(this, fragment, null, null, null));
    }
}
