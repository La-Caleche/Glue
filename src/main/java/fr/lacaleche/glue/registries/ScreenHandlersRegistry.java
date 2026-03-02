package fr.lacaleche.glue.registries;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.function.Function;

public class ScreenHandlersRegistry extends GlueRegistry {

    public ScreenHandlersRegistry(String modId) {
        super(modId);
    }

    public ScreenHandlersRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public <T extends AbstractContainerMenu, D extends CustomPacketPayload> ExtendedScreenHandlerType<T, D> register(
            String id, ExtendedScreenHandlerType.ExtendedFactory<T, D> factory,
            StreamCodec<? super RegistryFriendlyByteBuf, D> packetCodec
    ) {
        return Registry.register(BuiltInRegistries.MENU, this.id(id), new ExtendedScreenHandlerType<>(factory, packetCodec));
    }
}
