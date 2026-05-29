package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.ItemsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.items.TestComponentItem;
import net.minecraft.world.item.Item;

public class TestItems {

    public static final ItemsRegistry REGISTRY = new ItemsRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final Item TEST_OUTLINE_BLOCK = REGISTRY.register(TestBlocks.TEST_OUTLINE_BLOCK,
            new Item.Properties());
    public static final Item TEST_SPINNING_BLOCK = REGISTRY.register(TestBlocks.TEST_SPINNING_BLOCK,
            new Item.Properties());
    public static final Item TEST_SHADER_BLOCK = REGISTRY.register(TestBlocks.TEST_SHADER_BLOCK,
            new Item.Properties());
    public static final Item TEST_ADDITIVE_SPRITE_BLOCK = REGISTRY.register(TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK,
            new Item.Properties());
    public static final Item TEST_SHAPE_BLOCK = REGISTRY.register(TestBlocks.TEST_SHAPE_BLOCK,
            new Item.Properties());

    public static final Item TEST_COMPONENT_ITEM = REGISTRY.register("test_component",
            TestComponentItem::new, new Item.Properties());

    public static void registerItems() {
        TestmodClient.LOGGER.info("Registering items");
    }

}
