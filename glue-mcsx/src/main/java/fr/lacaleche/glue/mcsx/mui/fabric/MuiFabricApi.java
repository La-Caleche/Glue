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

package fr.lacaleche.glue.mcsx.mui.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.mcsx.mui.ModernUIMod;
import fr.lacaleche.glue.mcsx.mui.MuiModApi;
import fr.lacaleche.glue.mcsx.mui.MuiScreen;
import fr.lacaleche.glue.mcsx.mui.ScreenCallback;
import fr.lacaleche.glue.mcsx.mui.UIManager;
import icyllis.modernui.fragment.Fragment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.screens.Screen;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;

/**
 * Fabric implementation of {@link MuiModApi}.
 * <p>
 * Vendored from ModernUI-MC (LGPL); menu screens, post effects, rarity styling,
 * picture-in-picture, scissor peek and render-type factory were stripped.
 */
public final class MuiFabricApi extends MuiModApi {

    public MuiFabricApi() {
        ModernUIMod.LOGGER.info(ModernUIMod.MARKER, "Created MuiFabricApi");
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends Screen & MuiScreen> T createScreen(@NonNull Fragment fragment,
                                                         @Nullable ScreenCallback callback,
                                                         @Nullable Screen previousScreen,
                                                         @Nullable CharSequence title) {
        return (T) new SimpleScreen(UIManager.getInstance(),
                fragment, callback, previousScreen, title);
    }

    @Override
    public boolean isKeyBindingMatches(KeyMapping keyMapping, InputConstants.Key key) {
        return key.getType() == InputConstants.Type.KEYSYM
                ? keyMapping.matches(key.getValue(), InputConstants.UNKNOWN.getValue())
                : keyMapping.matches(InputConstants.UNKNOWN.getValue(), key.getValue());
    }

    @Override
    public GpuDevice getRealGpuDevice() {
        return RenderSystem.getDevice();
    }

    @Override
    public void submitGuiElementRenderState(GuiGraphics graphics, GuiElementRenderState renderState) {
        graphics.guiRenderState.submitGuiElement(renderState);
    }
}
