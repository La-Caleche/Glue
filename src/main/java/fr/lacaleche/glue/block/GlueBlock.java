package fr.lacaleche.glue.block;

import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.internal.GlueOutlineRenderers;

public interface GlueBlock {

    default GlueOutlineRenderer getOutlineRenderer() {
        return GlueOutlineRenderers.BASE_OUTLINE;
    }

}
