package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.shaper.VoxelShaper;
import fr.lacaleche.glue.testmod.blocks.demo.TestShapeBlock;
import fr.lacaleche.glue.testmod.registries.TestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Generated shapes reach the game on both sides: every state of the shape block has the outline and
 * collision its models describe, which are the boxes the block used to declare in Java.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Fabric discovers runTest, not JUnit annotations.
public final class ShapesClientTest extends WorldClientTest {

    /** Per mode, facing north: the shapes the block declared in Java before they were generated. */
    private static final List<VoxelShape> NORTH_SHAPES = List.of(
            Shapes.or(Block.box(2, 0, 0, 14, 4, 16), Block.box(2, 4, 10, 14, 14, 16)),
            Shapes.or(Block.box(5, 5, 0, 11, 11, 10), Block.box(3, 3, 10, 13, 13, 16)),
            Shapes.or(Block.box(3, 2, 0, 13, 14, 16), Block.box(5, 0, 0, 11, 16, 16)),
            Shapes.or(Block.box(2, 2, 0, 14, 14, 3), Block.box(2, 2, 13, 14, 14, 16), Block.box(5, 5, 3, 11, 11, 13)));

    @Override
    protected void test() {
        BlockPos origin = this.world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().blockPosition().above(3));
        List<Placed> placed = new ArrayList<>();
        int column = 0;
        for (int mode = 0; mode <= TestShapeBlock.MAX_MODE; mode++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = TestBlocks.TEST_SHAPE_BLOCK.defaultBlockState()
                        .setValue(TestShapeBlock.FACING, facing)
                        .setValue(TestShapeBlock.MODE, mode);
                placed.add(new Placed(origin.east(2 * column++), state, mode, facing));
            }
        }

        this.world.getServer().runOnServer(server -> {
            for (Placed block : placed) server.overworld().setBlock(block.pos(), block.state(), Block.UPDATE_ALL);
            for (Placed block : placed) check("server", server.overworld(), block);
        });
        waitUntil("the shape blocks reach the client", client -> client.level != null
                && placed.stream().allMatch(block -> client.level.getBlockState(block.pos()) == block.state()));
        this.context.runOnClient(client -> {
            for (Placed block : placed) check("client", client.level, block);
        });
    }

    private static void check(String side, BlockGetter level, Placed block) {
        VoxelShape expected = VoxelShaper.rotate(NORTH_SHAPES.get(block.mode()), Direction.NORTH, block.facing());
        VoxelShape outline = block.state().getShape(level, block.pos(), CollisionContext.empty());
        VoxelShape collision = block.state().getCollisionShape(level, block.pos(), CollisionContext.empty());
        require(!Shapes.joinIsNotEmpty(expected, outline, BooleanOp.NOT_SAME),
                side + " outline of " + block.state() + " is " + outline.toAabbs() + ", expected " + expected.toAabbs());
        require(!Shapes.joinIsNotEmpty(expected, collision, BooleanOp.NOT_SAME),
                side + " collision of " + block.state() + " is " + collision.toAabbs() + ", expected " + expected.toAabbs());
    }

    private record Placed(BlockPos pos, BlockState state, int mode, Direction facing) {
    }
}
