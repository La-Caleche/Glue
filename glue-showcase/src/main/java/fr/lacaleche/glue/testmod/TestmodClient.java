package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.client.debug.DebugManager;
import fr.lacaleche.glue.client.debug.RaycastDebugRenderer;
import fr.lacaleche.glue.testmod.gametest.ShowcaseGameTests;
import fr.lacaleche.glue.testmod.gametest.mcsx.McsxLifecycleGameTest;
import fr.lacaleche.glue.testmod.gametest.mcsx.expedition.ExpeditionGameTest;
import fr.lacaleche.glue.testmod.gametest.mcsx.playground.ModernUiGameTest;
import fr.lacaleche.glue.testmod.gametest.mcsx.studio.AxiomCompatGameTest;
import fr.lacaleche.glue.testmod.gametest.mcsx.studio.GlueStudioGameTest;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionDemo;
import fr.lacaleche.glue.testmod.mcsx.studio.StudioSession;
import fr.lacaleche.glue.testmod.registries.TestBlocksRenderer;
import fr.lacaleche.glue.testmod.registries.TestKeybinds;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import fr.lacaleche.glue.testmod.render.AutoScreenshot;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Client entry point for the Glue test mod: wires up the client-only demos &mdash; keybinds,
 * block-entity renderers and render layers, shader/post-effect registrations, the MCSX demos, and
 * the scripted gametests. The synced-registry content (blocks, items, components, block entities,
 * creative tab) is registered by {@link Testmod} so it exists on both sides. Start in either entry
 * point to trace what each feature demonstrates, or see {@code glue-showcase/README.md}.
 */
public class TestmodClient implements ClientModInitializer {

    public static final String MOD_ID = Testmod.MOD_ID;
    public static final Logger LOGGER = Testmod.LOGGER;
    private static TestmodClient instance;
    private RaycastDebugRenderer raycastDebugRenderer;

    public static TestmodClient getInstance() {
        return instance;
    }

    public static ResourceLocation id(String path) {
        return Testmod.id(path);
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        this.raycastDebugRenderer = new RaycastDebugRenderer();
        DebugManager.getInstance().register(this.raycastDebugRenderer);

        TestKeybinds.register();
        TestBlocksRenderer.registerBlocksRenderer();
        TestShaders.registerShaders();

        TestPostShaderHandler.INSTANCE.register();
        ExpeditionDemo.INSTANCE.init();
        StudioSession.INSTANCE.init();

        AutoScreenshot.init();
        ShowcaseGameTests.register();
        ModernUiGameTest.register();
        ExpeditionGameTest.register();
        GlueStudioGameTest.register();
        AxiomCompatGameTest.register();
        McsxLifecycleGameTest.register();
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
