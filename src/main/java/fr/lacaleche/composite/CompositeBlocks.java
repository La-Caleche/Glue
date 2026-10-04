package fr.lacaleche.composite;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.registries.BlocksRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * The composite block and its block entity. They keep Glue's namespace, so that saved cells load,
 * while composite cells ship inside Glue.
 */
public final class CompositeBlocks {

    public static final BlocksRegistry BLOCKS = new BlocksRegistry(Glue.MOD_ID);
    public static final BlockEntitiesRegistry BLOCK_ENTITIES = new BlockEntitiesRegistry(Glue.MOD_ID);

    /** The block of a composite cell; see {@link CompositeCells}. */
    public static final Block COMPOSITE = BLOCKS.register("composite", CompositeBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(CompositeBlock.LIGHT))
                    .dynamicShape()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    public static final BlockEntityType<CompositeBlockEntity> COMPOSITE_ENTITY =
            BLOCK_ENTITIES.register("composite", CompositeBlockEntity::new, COMPOSITE);

    private CompositeBlocks() {
    }

    /** Registers the block and block entity, by loading this class. */
    static void register() {
        Composite.LOGGER.info("Registering composite blocks");
    }
}
