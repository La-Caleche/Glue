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

import icyllis.modernui.annotation.UiThread;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.fragment.OnBackPressedDispatcher;
import net.minecraft.client.gui.screens.Screen;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;

/**
 * Common interface for Modern UI screens.
 */
public interface MuiScreen {

    /**
     * @return this as screen
     */
    @NonNull
    Screen self();

    /**
     * @return the main fragment
     */
    @NonNull
    Fragment getFragment();

    /**
     * @return a callback describes the screen properties
     */
    @Nullable
    ScreenCallback getCallback();

    /**
     * Returns the previous screen associated with this screen.
     * If non-null, then {@link #onBackPressed()} will return back to that screen.
     */
    @Nullable
    Screen getPreviousScreen();

    /**
     * Returns whether this is a container menu screen.
     *
     * @return true for MenuScreen, false for SimpleScreen
     */
    boolean isMenuScreen();

    /**
     * Call {@link OnBackPressedDispatcher#onBackPressed()} programmatically.
     */
    @UiThread
    void onBackPressed();
}
