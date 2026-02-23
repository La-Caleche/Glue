package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.registries.ItemGroupsRegistry;
import fr.lacaleche.glue.registries.ItemsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.block.outline.ExampleBlockOutlineRenderer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.impl.itemgroup.FabricItemGroupBuilderImpl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class TestItemGroups {

    public static final ItemGroupsRegistry REGISTRY = new ItemGroupsRegistry(TestmodClient.MOD_ID);

    public static final CreativeModeTab TEST_GROUP = REGISTRY.register(
            "test_group",
            FabricItemGroup.builder().title(Component.translatable("itemGroup.glue-test.test-group"))
                    .icon(() -> new ItemStack(TestItems.TEST_DEBUG)).displayItems((displayContext, entries) -> {
                        entries.accept(TestItems.TEST_DEBUG);
                        entries.accept(TestItems.TEST_COMPONENT_ITEM);
                        entries.accept(TestItems.TEST_SPINNING_BLOCK);
                    })
    );

    public static void registerItemGroups() {
        TestmodClient.LOGGER.info("Registering outline renderers");
    }

}
