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
import icyllis.modernui.annotation.MainThread;
import icyllis.modernui.annotation.RenderThread;
import icyllis.modernui.annotation.UiThread;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.KeyEvent;
import net.minecraft.client.Minecraft;

import icyllis.modernui.annotation.NonNull;

/**
 * Callback of a screen. Methods will be invoked from different threads.
 * Make your main {@link Fragment} subclass implement this interface, or use defaults.
 */
public interface ScreenCallback {

    /**
     * Determine whether the key event is considered as a back key.
     *
     * @param keyCode the key code, like {@link KeyEvent#KEY_E} (equivalent to GLFW)
     * @param event   the key event
     * @return whether the key event is considered as a back key
     */
    @UiThread
    default boolean isBackKey(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEY_ESCAPE)
            return true;
        InputConstants.Key key = InputConstants.getKey(keyCode, event.getScanCode());
        return MuiModApi.get().isKeyBindingMatches(
                Minecraft.getInstance().options.keyInventory,
                key
        );
    }

    /**
     * Should the screen be closed by the user. Default value: true
     *
     * @return whether the screen should close
     */
    @MainThread
    default boolean shouldClose() {
        return true;
    }

    /**
     * Determine whether the screen should pause the game. Default value: false
     *
     * @return whether to pause game
     */
    @MainThread
    default boolean isPauseScreen() {
        return false;
    }

    /**
     * Determine whether the screen should draw a default background. Default value: true
     *
     * @return whether to draw a default background
     */
    @RenderThread
    default boolean hasDefaultBackground() {
        return true;
    }

    /**
     * Determine whether the screen should blur the game scene when opened. Default value: true
     *
     * @return whether the game world should be blurred
     */
    @RenderThread
    default boolean shouldBlurBackground() {
        return true;
    }
}
