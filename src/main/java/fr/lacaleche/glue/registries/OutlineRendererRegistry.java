package fr.lacaleche.glue.registries;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.function.Supplier;

public class OutlineRendererRegistry extends GlueRegistry {

    public OutlineRendererRegistry(String modId) {
        super(modId);
    }

    public OutlineRendererRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public GlueOutlineRenderer register(String name, Supplier<GlueOutlineRenderer> factory) {
        return Registry.register(GlueClientRegistries.OUTLINE_RENDERERS, this.id(name), factory.get());
    }

}
