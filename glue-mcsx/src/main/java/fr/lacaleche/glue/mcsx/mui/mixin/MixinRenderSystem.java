/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Vendored and stripped for Ignis (LGPL): the bootstrap-property reads
 * (staging buffers / SPIR-V) and GPU driver-bug workarounds were removed.
 */

package fr.lacaleche.glue.mcsx.mui.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.mcsx.mui.fabric.UIManagerFabric;
import icyllis.arc3d.engine.ContextOptions;
import icyllis.modernui.core.Core;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

/**
 * Boots the Modern UI render stack at the moment Minecraft initializes its
 * renderer (render thread, GL context current): binds Modern UI's render thread
 * ({@link Core#initialize()}), initializes the Arc3D OpenGL backend, then starts
 * the {@link UIManagerFabric}. This is the canonical bootstrap site — the UI
 * thread's {@code init()} and the Arc3D immediate context both require it.
 */
@Mixin(RenderSystem.class)
public class MixinRenderSystem {

    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private static void mcsx$onInitRenderer(long window, int debugLevel, boolean debugSync,
                                             BiFunction<ResourceLocation, ShaderType, String> defaultShaderSource,
                                             boolean enableDebugLabels, CallbackInfo ci) {
        Core.initialize();
        ContextOptions options = new ContextOptions();
        if (!Core.initOpenGL(options)) {
            Core.glShowCapsErrorDialog();
            return;
        }
        UIManagerFabric.initialize();
    }

    /**
     * @author Ignis (vendored from Modern UI)
     * @reason Modern UI / Arc3D drive GL from their own contexts on the render
     * thread; disable Minecraft's render-thread assertion to avoid false positives.
     */
    @Overwrite(remap = false)
    public static void assertOnRenderThread() {
    }
}
