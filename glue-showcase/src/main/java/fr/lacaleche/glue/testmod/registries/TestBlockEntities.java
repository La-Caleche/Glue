package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TickingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Demonstrates Glue's {@link BlockEntitiesRegistry}: every type uses the factory-receives-type
 * overload, so entities that pass their own type to the constructor need no self-referential
 * supplier. The outline and spinning blocks share the stateless
 * {@link fr.lacaleche.glue.testmod.blocks.demo.TickingBlockEntity}; the shader block
 * (cycling index) and the additive-sprite block (light handle) keep their own stateful entities.
 */
public class TestBlockEntities {

    public static final BlockEntitiesRegistry REGISTRY = new BlockEntitiesRegistry(Testmod.MOD_ID, Testmod::id);

    public static final BlockEntityType<TickingBlockEntity> OUTLINE_BLOCK_ENTITY =
            REGISTRY.register("test_outline_block", TickingBlockEntity::new, TestBlocks.TEST_OUTLINE_BLOCK);

    public static final BlockEntityType<TickingBlockEntity> SPINNING_BLOCK_ENTITY =
            REGISTRY.register("test_spinning_block", TickingBlockEntity::new, TestBlocks.TEST_SPINNING_BLOCK);

    public static final BlockEntityType<TestShaderBlockEntity> SHADER_BLOCK_ENTITY =
            REGISTRY.register("test_shader_block", TestShaderBlockEntity::new, TestBlocks.TEST_SHADER_BLOCK);

    public static final BlockEntityType<TestAdditiveSpriteBlockEntity> ADDITIVE_SPRITE_BLOCK_ENTITY =
            REGISTRY.register("test_additive_sprite_block", TestAdditiveSpriteBlockEntity::new,
                    TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK);

    public static void registerBlockEntities() {
        Testmod.LOGGER.info("Registering block entities");
    }
}
