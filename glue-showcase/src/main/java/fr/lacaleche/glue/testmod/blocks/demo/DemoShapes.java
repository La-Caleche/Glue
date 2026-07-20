package fr.lacaleche.glue.testmod.blocks.demo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shapes shared by the demo blocks. */
public final class DemoShapes {

    /** A tall column on a wide base, used by the blocks that render something above themselves. */
    public static final VoxelShape PEDESTAL = Shapes.join(
            Block.box(0, 4, 0, 16, 22, 16),
            Block.box(-3, 0, -3, 19, 4, 19),
            BooleanOp.OR);

    private DemoShapes() {
    }
}
