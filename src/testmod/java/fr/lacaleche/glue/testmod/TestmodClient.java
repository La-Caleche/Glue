package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.debug.RaycastDebugRenderer;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.testmod.registries.*;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import fr.lacaleche.glue.testmod.render.PostEffectDebugHud;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import fr.lacaleche.glue.testmod.render.TestShaderPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestmodClient implements ClientModInitializer {

    public static final String MOD_ID = "glue-test";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static TestmodClient instance;
    private RaycastDebugRenderer raycastDebugRenderer;

    public static TestmodClient getInstance() {
        return instance;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        this.raycastDebugRenderer = new RaycastDebugRenderer();
        DebugManager.getInstance().register(this.raycastDebugRenderer);

        TestKeybinds.registerKeybinds();

        TestBlocks.registerBlocks();
        TestItems.registerItems();
        TestDataComponents.registerDataComponents();

        TestItemGroups.registerItemGroups();

        TestBlockEntities.registerBlockEntities();
        TestBlocksRenderer.registerBlocksRenderer();



        TestShaders.registerShaders();
        TestPostShaderHandler.INSTANCE.register();
        TestShaderPipelines.init();

        PostEffectDebugHud.INSTANCE.init();

        // Post-effect debug HUD lifecycle
        RenderEvents.RENDER_HUD.register(guiGraphics -> {
            if (PostEffectDebugHud.INSTANCE.isActive()) {
                PostEffectDebugHud.INSTANCE.render(guiGraphics);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PostEffectDebugHud.INSTANCE.tick();
        });

        AdditiveSpriteRenderer.init();
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
