package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

public class ItemsRegistry extends GlueRegistry {

    public ItemsRegistry(String modId) {
        super(modId);
    }

    public ItemsRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public Item register(ResourceKey<Item> itemKey, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    public Item register(ResourceLocation resourceLocation, Function<Item.Properties, Item> factory,
                         Item.Properties settings) {
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, resourceLocation);
        Item item = factory.apply(settings.setId(itemKey));
        return register(itemKey, item);
    }

    public Item register(String name, Function<Item.Properties, Item> factory, Item.Properties settings) {
        final ResourceLocation identifier = this.id(name);
        return register(identifier, factory, settings);
    }

    public Item register(Block block, Item.Properties settings) {
        final ResourceLocation identifier = this.id(BuiltInRegistries.BLOCK.getKey(block).getPath());
        return register(identifier, properties -> new BlockItem(block, properties), settings);
    }

    public Item register(Block block) {
        return register(block, new Item.Properties());
    }

}
