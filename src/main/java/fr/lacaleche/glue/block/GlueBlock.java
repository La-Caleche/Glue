package fr.lacaleche.glue.block;

import fr.lacaleche.glue.Glue;
import net.minecraft.resources.ResourceLocation;

public interface GlueBlock {

    default ResourceLocation getOutlineRenderer() {
        return Glue.id("base");
    }

}
