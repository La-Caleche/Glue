package fr.lacaleche.glue.registries;

import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.render.outline.SimpleBlockOutlineRenderer;
import fr.lacaleche.glue.internal.GlueRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class OutlineRendererRegistry extends GlueRegistry {

    public OutlineRendererRegistry(String modId) {
        super(modId);
    }

    public OutlineRendererRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public GlueOutlineRenderer register(String name, Supplier<GlueOutlineRenderer> factory) {
        return Registry.register(GlueRegistries.OUTLINE_RENDERERS, this.id(name), factory.get());
    }

}
