package fr.lacaleche.glue.internal;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.registries.DataComponentTypesRegistry;
import net.minecraft.core.component.DataComponentType;

public class GlueComponentTypes {

    public static final DataComponentTypesRegistry REGISTRY = new DataComponentTypesRegistry(Glue.MOD_ID, Glue::id);

    public static final DataComponentType<TransformationComponent> TRANSFORMATION = REGISTRY.register(
            "glue_transform", builder -> builder.persistent(TransformationComponent.CODEC).networkSynchronized(TransformationComponent.PACKET_CODEC)
    );

    public static void registerComponentTypes() {
        Glue.LOGGER.info("Registering Glue components");
    }

}
