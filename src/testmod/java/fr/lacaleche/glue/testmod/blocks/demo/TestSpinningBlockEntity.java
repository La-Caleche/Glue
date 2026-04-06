package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TestSpinningBlockEntity extends BlockEntity {

    private int ticks = 0;

    public TestSpinningBlockEntity(BlockPos pos, BlockState blockState) {
        super(TestBlockEntities.SPINNING_BLOCK_ENTITY, pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestSpinningBlockEntity entity) {
        entity.ticks++;
    }

    public int getTicks() {
        return this.ticks;
    }
}
