package fr.lacaleche.glue.client.registries;

import com.google.common.collect.Maps;
import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.render.outline.SimpleBlockOutlineRenderer;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;

public class GlueOutlineRenderers {

    private static final Map<GlueOutlineRenderer, Set<Block>> OUTLINE_RENDERERS = Maps.newHashMap();

    public static final GlueOutlineRenderer BASE_OUTLINE = register(new SimpleBlockOutlineRenderer());

    public static void registerOutlineRenderers() {
        Glue.LOGGER.info("Registering outline renderers");
    }

    public static GlueOutlineRenderer register(GlueOutlineRenderer outlineRenderer, Block... blocks) {
        OUTLINE_RENDERERS.put(outlineRenderer, Set.of(blocks));
        return outlineRenderer;
    }

    public static Map<GlueOutlineRenderer, Set<Block>> getOutlineRenderers() {
        return Map.copyOf(OUTLINE_RENDERERS);
    }

}
