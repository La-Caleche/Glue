package fr.lacaleche.glue.internal;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.composite.CompositeBlock;
import fr.lacaleche.glue.composite.CompositeBlockEntity;
import fr.lacaleche.glue.registries.BlockEntitiesRegistry;
import fr.lacaleche.glue.registries.BlocksRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public class GlueBlocks {

    public static final BlocksRegistry BLOCKS = new BlocksRegistry(Glue.MOD_ID);
    public static final BlockEntitiesRegistry BLOCK_ENTITIES = new BlockEntitiesRegistry(Glue.MOD_ID);

    /** The block of a composite cell; see {@link fr.lacaleche.glue.composite.CompositeCells}. */
    public static final Block COMPOSITE = BLOCKS.register("composite", CompositeBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .dynamicShape()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    public static final BlockEntityType<CompositeBlockEntity> COMPOSITE_ENTITY =
            BLOCK_ENTITIES.register("composite", CompositeBlockEntity::new, COMPOSITE);

    public static void registerBlocks() {
        Glue.LOGGER.info("Registering Glue blocks");
    }
}
