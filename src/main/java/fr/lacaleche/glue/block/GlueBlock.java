package fr.lacaleche.glue.block;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.internal.GlueOutlineRenderers;
import net.minecraft.resources.ResourceLocation;

public interface GlueBlock {

    default ResourceLocation getOutlineRenderer() {
        return Glue.id("base");
    }

}
