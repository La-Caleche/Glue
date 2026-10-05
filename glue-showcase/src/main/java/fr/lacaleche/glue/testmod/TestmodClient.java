package fr.lacaleche.glue.testmod;

import fr.lacaleche.glue.testmod.registries.TestBlocksRenderer;
import fr.lacaleche.glue.testmod.registries.TestKeybinds;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import fr.lacaleche.glue.testmod.ui.ShowcaseDeveloperPage;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Client entry point for the Glue test mod: wires up the client-only demos &mdash; keybinds,
 * block-entity renderers and render layers, shader/post-effect registrations and commands.
 * The synced-registry content (blocks, items, components, block entities,
 * creative tab) is registered by {@link Testmod} so it exists on both sides. Start in either entry
 * point to trace what each feature demonstrates, or see {@code glue-showcase/README.md}.
 */
public class TestmodClient implements ClientModInitializer {

    public static final String MOD_ID = Testmod.MOD_ID;
    public static final Logger LOGGER = Testmod.LOGGER;
    private static TestmodClient instance;

    public static TestmodClient getInstance() {
        return instance;
    }

    public static ResourceLocation id(String path) {
        return Testmod.id(path);
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        TestKeybinds.register();
        TestBlocksRenderer.registerBlocksRenderer();
        TestShaders.registerShaders();

        TestPostShaderHandler.INSTANCE.register();
        ShowcaseCommands.register();
        ShowcaseDeveloperPage.register();

        AdditiveSpriteRenderer.init();
    }
}
