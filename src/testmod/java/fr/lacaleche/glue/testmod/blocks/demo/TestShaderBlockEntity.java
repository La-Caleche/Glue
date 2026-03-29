package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity providing tick-based animation data for the shader block renderer.
 */
public class TestShaderBlockEntity extends BlockEntity {

    /** Animation tick counter */
    private int ticks = 0;

    public TestShaderBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.SHADER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestShaderBlockEntity entity) {
        entity.ticks++;
    }

    public int getTicks() {
        return this.ticks;
    }

    /**
     * @return Normalized time value for smooth animations (0-1 cycling)
     */
    public float getAnimationProgress() {
        return (this.ticks % 200) / 200f;
    }
}
