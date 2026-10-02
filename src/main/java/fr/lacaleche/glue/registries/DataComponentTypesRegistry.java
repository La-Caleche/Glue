package fr.lacaleche.glue.registries;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public class DataComponentTypesRegistry extends GlueRegistry {

    public DataComponentTypesRegistry(String modId) {
        super(modId);
    }

    public DataComponentTypesRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public <T> DataComponentType<T> register(
            String path, Codec<T> codec,
            StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec
    ) {
        return this.register(path, builder -> builder.persistent(codec).networkSynchronized(streamCodec));
    }

    public <T> DataComponentType<T> register(String path, UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        DataComponentType.Builder<T> builder = DataComponentType.builder();
        builderOperator.apply(builder);
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, this.id(path), builder.build());
    }
}
