package fr.lacaleche.glue.registries;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class ScreenHandlersRegistry {

  private final String modId;

  public ScreenHandlersRegistry(String modId) {
    this.modId = modId;
  }

  private ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(this.modId, path);
  }

  public <T extends AbstractContainerMenu, D extends CustomPacketPayload> ExtendedScreenHandlerType<T, D> register(
      String id, ExtendedScreenHandlerType.ExtendedFactory<T, D> factory,
      StreamCodec<? super RegistryFriendlyByteBuf, D> packetCodec) {
    return Registry.register(BuiltInRegistries.MENU, this.id(id),
        new ExtendedScreenHandlerType<>(factory, packetCodec));
  }
}
