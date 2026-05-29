package fr.lacaleche.glue.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.debug.FboDebugHud;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.ShaderContext;
import fr.lacaleche.glue.client.shader.internal.DeferredDrawQueue;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinitionLoader;
import fr.lacaleche.glue.client.shader.pipeline.PipelineDefinitionLoader;
import fr.lacaleche.glue.client.render.outline.OutlineDefinitionLoader;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

public class GlueClient implements ClientModInitializer {

    private static final KeyMapping FBO_DEBUG_KEY = new KeyMapping(
            "key.glue.fbo_debug_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.glue"
    );

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();
        GlueClientRegistries.bootstrap();

        DrawSelectionEvents.BLOCK.register(BlockRenderer::drawBlockOutline);
        ParticleManagerEvents.BLOCK_BREAK.register(BlockRenderer::getBreakParticleShape);

        DeferredDrawQueue.INSTANCE.register();

        WorldRenderEvents.START.register(ctx -> RenderCompat.resetFrameCache());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ShaderContext.get().cleanup());

        // Clear post-shader handle warnings on resource reload so missing chains are
        // re-reported if the resource pack changes.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath("glue", "post_shader_warnings_reset");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        PostShaderHandle.clearWarnings();
                    }
                }
        );

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new PipelineDefinitionLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new TimedEffectDefinitionLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new OutlineDefinitionLoader());

        KeyBindingHelper.registerKeyBinding(FBO_DEBUG_KEY);

        RenderEvents.RENDER_HUD.register(guiGraphics -> {
            if (FboDebugHud.INSTANCE.isActive()) {
                FboDebugHud.INSTANCE.render(guiGraphics);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;
            while (FBO_DEBUG_KEY.consumeClick()) {
                FboDebugHud.INSTANCE.toggle();
            }
            FboDebugHud.INSTANCE.tick();
        });

        RenderEvents.POST_WORLD_RENDER.register(() -> FboDebugHud.INSTANCE.captureDepthNow());

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
