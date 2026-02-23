package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.block.outline.ExampleBlockOutlineRenderer;

public class TestOutlineRenderers {

    public static final GlueOutlineRenderer EXAMPLE_OUTLINE = GlueOutlineRenderers.register(new ExampleBlockOutlineRenderer(), TestBlocks.TEST_DEBUG_BLOCK);

    public static void registerOutlineRenderer() {
        TestmodClient.LOGGER.info("Registering outline renderers");
    }

}
