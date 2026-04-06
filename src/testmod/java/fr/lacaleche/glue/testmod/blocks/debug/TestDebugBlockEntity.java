package fr.lacaleche.glue.testmod.blocks.debug;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TestDebugBlockEntity extends BlockEntity {

    private int ticks = 0;

    public TestDebugBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.DEBUG_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestDebugBlockEntity entity) {
        entity.ticks++;
    }

    public int getTicks() {
        return ticks;
    }
}
