package fr.lacaleche.glue.testmod.blocks.debug;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TestDebugBlockEntity extends BlockEntity {

    private long ticks = 0;

    public TestDebugBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.DEBUG_BLOCK_ENTITY, pos, state);
    }

    public void tick() {
        this.ticks++;
    }

    public long getTicks() {
        return this.ticks;
    }
}
