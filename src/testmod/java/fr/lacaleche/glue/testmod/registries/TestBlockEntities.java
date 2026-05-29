package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestOutlineBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class TestBlockEntities {

    public static final BlockEntitiesRegistry REGISTRY = new BlockEntitiesRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static void registerBlockEntities() {
        TestmodClient.LOGGER.info("Registering block entities");
    }

    public static final BlockEntityType<TestOutlineBlockEntity> OUTLINE_BLOCK_ENTITY = REGISTRY.register(
            "test_outline_block",
            FabricBlockEntityTypeBuilder.create(TestOutlineBlockEntity::new, TestBlocks.TEST_OUTLINE_BLOCK).build());

    public static final BlockEntityType<TestSpinningBlockEntity> SPINNING_BLOCK_ENTITY = REGISTRY.register(
            "test_spinning_block",
            FabricBlockEntityTypeBuilder.create(TestSpinningBlockEntity::new, TestBlocks.TEST_SPINNING_BLOCK).build());

    public static final BlockEntityType<TestShaderBlockEntity> SHADER_BLOCK_ENTITY = REGISTRY.register(
            "test_shader_block",
            FabricBlockEntityTypeBuilder.create(TestShaderBlockEntity::new, TestBlocks.TEST_SHADER_BLOCK).build());

    public static final BlockEntityType<TestAdditiveSpriteBlockEntity> ADDITIVE_SPRITE_BLOCK_ENTITY = REGISTRY.register(
            "test_additive_sprite_block",
            FabricBlockEntityTypeBuilder.create(TestAdditiveSpriteBlockEntity::new, TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK).build());


}
