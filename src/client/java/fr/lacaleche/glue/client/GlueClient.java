package fr.lacaleche.glue.client;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.debug.DeveloperMenu;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.client.render.model.GlueBlockModels;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.ShaderContext;
import fr.lacaleche.glue.client.shader.internal.DeferredDrawQueue;
import fr.lacaleche.glue.client.shader.effect.PostChainDefinitionLoader;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinitionLoader;
import fr.lacaleche.glue.client.shader.pipeline.PipelineDefinitionLoader;
import fr.lacaleche.glue.client.render.outline.OutlineDefinitionLoader;
import fr.lacaleche.glue.client.utils.RaycastUtils;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public class GlueClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GlueOutlineRenderers.registerOutlineRenderers();

        DrawSelectionEvents.BLOCK.register(BlockRenderer::drawBlockOutline);
        ParticleManagerEvents.BLOCK_BREAK.register(BlockRenderer::getBreakParticleShape);

        DeferredDrawQueue.INSTANCE.register();

        GlueBlockModels.register();

        WorldRenderEvents.START.register(ctx -> RenderCompat.resetFrameCache());
        RaycastUtils.register();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ShaderContext.get().cleanup();
        });

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

        // Register loaders (timed effects depend on post chains via getFabricDependencies)
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new PostChainDefinitionLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new PipelineDefinitionLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new TimedEffectDefinitionLoader());
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new OutlineDefinitionLoader());

        DeveloperMenu.bootstrap();

        Glue.LOGGER.info("Glue Client library ready !");
    }
}
