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
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.*;

import icyllis.modernui.annotation.NonNull;

/**
 * Mod class, common only.
 * <p>
 * Vendored and stripped from ModernUI-MC (LGPL). Text-engine, OptiFine/Iris
 * integration and config bootstrap were removed for the minimal Ignis bridge.
 */
public abstract class ModernUIMod {

    public static final Logger LOGGER = LogManager.getLogger("ModernUI-MC");
    public static final Marker MARKER = MarkerManager.getMarker("Init");

    public static volatile boolean sDevelopment;

    @NonNull
    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModernUI.ID, path);
    }

    public static boolean isDeveloperMode() {
        return sDevelopment;
    }
}
