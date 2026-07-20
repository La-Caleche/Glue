package fr.lacaleche.glue.client.render.light;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import fr.lacaleche.glue.client.render.light.internal.ClientLightBridge;
import fr.lacaleche.glue.client.render.light.internal.ClientPersistentLights;
import fr.lacaleche.glue.client.render.light.internal.LightManager;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.lumos.Lumos;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GlueLumosClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LightRenderer.init();
        Lumos.installClientBridge(new ClientLightBridge());
        ClientPersistentLights.registerClient();
        TerrainMaterialBuffer.requestWhen(() -> !LightManager.getInstance().isEmpty());
        TerrainMaterialBuffer.releaseOnClose(LightRenderer::cleanup);
        switchWorld(Minecraft.getInstance().level);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> switchWorld(level));
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> invalidateChunk(level, chunk.getPos()));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> invalidateChunk(level, chunk.getPos()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> switchWorld(null));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return ResourceLocation.fromNamespaceAndPath("glue-lumos", "renderer");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        Minecraft.getInstance().execute(LightRenderer::reloadResources);
                    }
                });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            switchWorld(null);
            LightRenderer.cleanup();
        });
    }

    private static void switchWorld(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        if (!RenderSystem.isOnRenderThread()) {
            client.execute(() -> switchWorld(level));
            return;
        }
        LightManager manager = LightManager.getInstance();
        WorldLightContext previous = manager.switchWorld(level);
        if (previous != null) previous.close();
        LightRenderer.configureWorld(manager.currentWorld());
        ClientPersistentLights.onWorldSwitch(level);
    }

    private static void invalidateChunk(ClientLevel level, net.minecraft.world.level.ChunkPos chunk) {
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null && context.level() == level) context.invalidateChunk(chunk);
    }
}
