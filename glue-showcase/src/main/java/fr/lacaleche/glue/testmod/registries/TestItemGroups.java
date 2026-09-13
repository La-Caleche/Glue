package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.ItemGroupsRegistry;
import fr.lacaleche.glue.testmod.Testmod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Demonstrates Glue's {@link ItemGroupsRegistry}: registers a creative tab
 * containing all demo items.
 */
public class TestItemGroups {

    public static final ItemGroupsRegistry REGISTRY = new ItemGroupsRegistry(Testmod.MOD_ID, Testmod::id);

    public static final CreativeModeTab TEST_GROUP = REGISTRY.register(
            "test_group",
            FabricItemGroup.builder().title(Component.translatable("itemGroup.glue-test.test-group"))
                    .icon(() -> new ItemStack(TestItems.TEST_OUTLINE_BLOCK)).displayItems((displayContext, entries) -> {
                        entries.accept(TestItems.TEST_OUTLINE_BLOCK);
                        entries.accept(TestItems.TEST_COMPONENT_ITEM);
                        entries.accept(TestItems.TEST_SPINNING_BLOCK);
                        entries.accept(TestItems.TEST_SHADER_BLOCK);
                        entries.accept(TestItems.TEST_ADDITIVE_SPRITE_BLOCK);
                        entries.accept(TestItems.TEST_SHAPE_BLOCK);
                    })
    );

    public static void registerItemGroups() {
        Testmod.LOGGER.info("Registering item groups");
    }
}
