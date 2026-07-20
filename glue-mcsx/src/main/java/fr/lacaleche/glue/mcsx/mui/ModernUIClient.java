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

package fr.lacaleche.glue.mcsx.mui;

import icyllis.modernui.ModernUI;
import icyllis.modernui.view.WindowManager;

import icyllis.modernui.annotation.NonNull;

/**
 * Minimal Modern UI application instance for the Ignis bridge.
 * <p>
 * Vendored and heavily stripped from ModernUI-MC (LGPL): the font/typeface
 * loading, bootstrap-properties store, GPU driver-bug workarounds and
 * OptiFine/Iris shader detection were all removed. The base
 * {@link ModernUI} constructor wires up {@code Resources.getSystem()} and the
 * singleton, which is all the view system requires to resolve resources/theme.
 */
public class ModernUIClient extends ModernUI {

    private static volatile ModernUIClient sInstance;

    public ModernUIClient() {
        super();
        sInstance = this;
    }

    @NonNull
    public static ModernUIClient getInstance() {
        if (sInstance == null)
            throw new IllegalStateException("ModernUI mod client was never initialized. " +
                    "Please check whether mod loader threw an exception before.");
        return sInstance;
    }

    @Override
    public WindowManager getWindowManager() {
        return UIManager.getInstance().getDecorView();
    }
}
