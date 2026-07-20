package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import fr.lacaleche.glue.testmod.blocks.demo.TickingBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Demonstrates Glue's {@link BlockEntitiesRegistry}. Three blocks share the
 * stateless {@link fr.lacaleche.glue.testmod.blocks.demo.TickingBlockEntity};
 * the shader block uses its own stateful entity.
 */
public class TestBlockEntities {

    public static final BlockEntitiesRegistry REGISTRY = new BlockEntitiesRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static void registerBlockEntities() {
        TestmodClient.LOGGER.info("Registering block entities");
    }

    // The outline, spinning, and additive-sprite blocks only need a tick counter,
    // so they share TickingBlockEntity. Each factory passes the type it is building;
    public static final BlockEntityType<TickingBlockEntity> OUTLINE_BLOCK_ENTITY =
            ticking("test_outline_block", TestBlocks.TEST_OUTLINE_BLOCK, () -> TestBlockEntities.OUTLINE_BLOCK_ENTITY);

    public static final BlockEntityType<TickingBlockEntity> SPINNING_BLOCK_ENTITY =
            ticking("test_spinning_block", TestBlocks.TEST_SPINNING_BLOCK, () -> TestBlockEntities.SPINNING_BLOCK_ENTITY);

    // The shader block needs persistent state (cycling index), so it keeps a dedicated entity.
    public static final BlockEntityType<TestShaderBlockEntity> SHADER_BLOCK_ENTITY = REGISTRY.register(
            "test_shader_block",
            FabricBlockEntityTypeBuilder.create(TestShaderBlockEntity::new, TestBlocks.TEST_SHADER_BLOCK).build());

    public static final BlockEntityType<TickingBlockEntity> ADDITIVE_SPRITE_BLOCK_ENTITY =
            ticking("test_additive_sprite_block", TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK,
                    () -> TestBlockEntities.ADDITIVE_SPRITE_BLOCK_ENTITY);

    /**
     * A plain {@link TickingBlockEntity} type. The entity needs its own type to construct itself, which
     * the field is still being assigned when the factory is built &mdash; the supplier defers that read
     * to first use, when the field is set.
     */
    private static BlockEntityType<TickingBlockEntity> ticking(
            String name, Block block, Supplier<BlockEntityType<TickingBlockEntity>> self) {
        return REGISTRY.register(name, FabricBlockEntityTypeBuilder.create(
                (pos, state) -> new TickingBlockEntity(self.get(), pos, state), block).build());
    }
}
