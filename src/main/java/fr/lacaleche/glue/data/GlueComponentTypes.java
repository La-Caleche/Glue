package fr.lacaleche.glue.data;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;

public class GlueComponentTypes {
    public static final DataComponentType<TransformationComponent> TRANSFORMATION = register(
            "glue_transform", builder -> builder.persistent(TransformationComponent.CODEC).networkSynchronized(TransformationComponent.PACKET_CODEC)
    );

    public static void registerProperties() {
        Glue.LOGGER.info("Registering Glue components");
    }

    public static <T> DataComponentType<T> register(String id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return register(Glue.id(id), builderOperator.apply(DataComponentType.builder()).build());
    }

    public static <T> DataComponentType<T> register(ResourceLocation id, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return register(id, builderOperator.apply(DataComponentType.builder()).build());
    }

    private static <T> DataComponentType<T> register(ResourceLocation id, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, type);
    }
}
