package fr.lacaleche.glue.client.registries;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.render.outline.SimpleBlockOutlineRenderer;
import fr.lacaleche.glue.registries.GlueRegistry;
import fr.lacaleche.glue.registries.OutlineRendererRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class GlueOutlineRenderers extends GlueRegistry {

    public static final OutlineRendererRegistry REGISTRY = new OutlineRendererRegistry(Glue.MOD_ID, Glue::id);

    public static final GlueOutlineRenderer BASE_OUTLINE = REGISTRY.register("base", SimpleBlockOutlineRenderer::new);

    public GlueOutlineRenderers(String modId) {
        super(modId);
    }

    public GlueOutlineRenderers(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public static void registerOutlineRenderers() {
        Glue.LOGGER.info("Registering outline renderers");
    }

}
