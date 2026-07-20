package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.debug.RaycastDebugRenderer;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.testmod.mcsx.McsxDemos;
import fr.lacaleche.glue.testmod.registries.*;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import fr.lacaleche.glue.testmod.render.LightDebugHud;
import fr.lacaleche.glue.testmod.render.LightShapePreviewRenderer;
import fr.lacaleche.glue.testmod.render.PostEffectDebugHud;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point for the Glue test mod.
 *
 * <p>Wires up every demo: registers blocks/items/components/keybinds/shaders and
 * the post-effect debug HUD. This is the top of the dependency graph — start here
 * to trace what each feature demonstrates, or see {@code glue-showcase/README.md}.</p>
 */
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

        PostEffectDebugHud.INSTANCE.init();
        RenderEvents.RENDER_HUD.register(PostEffectDebugHud.INSTANCE::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> PostEffectDebugHud.INSTANCE.tick());

        // Light debug HUD (F12): panel + in-world shape preview
        DebugManager.getInstance().register(new LightShapePreviewRenderer());
        RenderEvents.RENDER_HUD.register(LightDebugHud.INSTANCE::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> LightDebugHud.INSTANCE.tick());

        AdditiveSpriteRenderer.init();

        McsxDemos.register();
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
