package fr.lacaleche.glue.client.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface ParticleManagerEvents {

    Event<Block> BLOCK_BREAK = EventFactory.createArrayBacked(Block.class, callbacks -> (blockState, world, blockView, blockPos) -> {
        VoxelShape result;
        for (Block e : callbacks) {
            if ((result = e.onGetBreakParticlesShape(blockState, world, blockView, blockPos)) != null)
                return result;
        }
        return blockState.getShape(world, blockPos);
    });

    @FunctionalInterface
    interface Block {
        VoxelShape onGetBreakParticlesShape(BlockState blockState, ClientLevel world, BlockGetter blockView, BlockPos blockPos);
    }

}
