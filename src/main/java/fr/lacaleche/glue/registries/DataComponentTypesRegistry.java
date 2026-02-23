package fr.lacaleche.glue.registries;

import com.mojang.serialization.Codec;
import fr.lacaleche.glue.Glue;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class DataComponentTypesRegistry {

  private final String modId;

  public DataComponentTypesRegistry(String modId) {
    this.modId = modId;
  }

  public <T> DataComponentType<T> register(String path, Codec<T> codec,
      StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
    return Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        Glue.id(path),
        DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
  }
}
