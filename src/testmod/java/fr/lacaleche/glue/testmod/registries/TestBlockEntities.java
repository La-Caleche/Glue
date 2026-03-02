package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.debug.TestDebugBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlockEntity; // Added import
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class TestBlockEntities {

    public static final BlockEntitiesRegistry REGISTRY = new BlockEntitiesRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final BlockEntityType<TestDebugBlockEntity> DEBUG_BLOCK_ENTITY = REGISTRY.register(
            "test_debug_block",
            FabricBlockEntityTypeBuilder.create(TestDebugBlockEntity::new, TestBlocks.TEST_DEBUG_BLOCK).build());

    public static final BlockEntityType<TestSpinningBlockEntity> SPINNING_BLOCK_ENTITY = REGISTRY.register(
            "test_spinning_block",
            FabricBlockEntityTypeBuilder.create(TestSpinningBlockEntity::new, TestBlocks.TEST_SPINNING_BLOCK).build());

    public static void registerBlockEntities() {
        TestmodClient.LOGGER.info("Registering block entities");
    }

}
