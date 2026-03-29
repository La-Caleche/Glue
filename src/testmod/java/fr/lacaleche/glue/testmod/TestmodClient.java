package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.debug.RaycastDebugRenderer;
import fr.lacaleche.glue.client.events.DrawSelectionEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import fr.lacaleche.glue.client.render.BlockRenderer;
import fr.lacaleche.glue.testmod.registries.*;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestmodClient implements ClientModInitializer {

    private static TestmodClient instance;
    public static final String MOD_ID = "glue-test";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private RaycastDebugRenderer raycastDebugRenderer;

    {
        instance = this;
    }

    public static TestmodClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        this.raycastDebugRenderer = new RaycastDebugRenderer();
        DebugManager.getInstance().register(this.raycastDebugRenderer);

        TestKeybinds.registerKeybinds();

        TestBlocks.registerBlocks();
        TestItems.registerItems();

        TestItemGroups.registerItemGroups();

        TestBlockEntities.registerBlockEntities();
        TestBlocksRenderer.registerBlocksRenderer();

        TestOutlineRenderers.registerOutlineRenderer();

        // Shader system
        TestShaders.registerShaders();
        TestPostShaderHandler.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public void toggleRaycastDebug() {
        if (this.raycastDebugRenderer != null) {
            this.raycastDebugRenderer.enabled = !this.raycastDebugRenderer.enabled;
            if (!this.raycastDebugRenderer.enabled) {
                this.raycastDebugRenderer.clear();
            }
        }
    }
}

