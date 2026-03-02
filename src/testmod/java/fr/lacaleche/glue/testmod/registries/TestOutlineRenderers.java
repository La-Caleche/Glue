package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.registries.OutlineRendererRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.block.outline.ExampleBlockOutlineRenderer;

public class TestOutlineRenderers {

    public static final OutlineRendererRegistry REGISTRY = new OutlineRendererRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final GlueOutlineRenderer EXAMPLE_OUTLINE = REGISTRY.register("example", ExampleBlockOutlineRenderer::new);

    public static void registerOutlineRenderer() {
        TestmodClient.LOGGER.info("Registering outline renderers");
    }

}
