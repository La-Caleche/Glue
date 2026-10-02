package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.shaper.VoxelShaper;
import fr.lacaleche.glue.testmod.blocks.demo.TestChairBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestShapeBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestStoveBlock;
import fr.lacaleche.glue.testmod.registries.TestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generated shapes reach the game on both sides: every state of the shape block has the outline and
 * collision its models describe, which are the boxes the block used to declare in Java. The chair
 * ported from Occamod turns its shape in sixteen steps, and the stove takes its collision from a
 * separate model.
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
        List<BlockState> chairs = TestBlocks.TEST_CHAIR_BLOCK.getStateDefinition().getPossibleStates();
        List<BlockState> stoves = TestBlocks.TEST_STOVE_BLOCK.getStateDefinition().getPossibleStates();
        BlockPos chairRow = origin.south(3);
        BlockPos stoveRow = origin.south(6);

        this.world.getServer().runOnServer(server -> {
            for (Placed block : placed) server.overworld().setBlock(block.pos(), block.state(), Block.UPDATE_ALL);
            for (int i = 0; i < chairs.size(); i++)
                server.overworld().setBlock(chairRow.east(2 * i), chairs.get(i), Block.UPDATE_ALL);
            for (int i = 0; i < stoves.size(); i++)
                server.overworld().setBlock(stoveRow.east(2 * i), stoves.get(i), Block.UPDATE_ALL);
            for (Placed block : placed) check("server", server.overworld(), block);
            checkChair("server", server.overworld(), chairRow);
            checkStove("server", server.overworld(), stoveRow);
        });
        waitUntil("the shape blocks reach the client", client -> client.level != null
                && placed.stream().allMatch(block -> client.level.getBlockState(block.pos()) == block.state())
                && client.level.getBlockState(stoveRow.east(2 * (stoves.size() - 1))) == stoves.getLast());
        this.context.runOnClient(client -> {
            for (Placed block : placed) check("client", client.level, block);
            checkChair("client", client.level, chairRow);
            checkStove("client", client.level, stoveRow);
        });

        double x = chairRow.getX() + 15.5;
        double y = chairRow.getY() + 6;
        double z = stoveRow.getZ() + 12.5;
        this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), x, y, z, Set.of(), 180, 25, false));
        waitUntil("the camera faces the Occamod rows", client -> client.player.position().distanceToSqr(x, y, z) < 0.01);
        this.context.waitTicks(20);
        screenshot("shapes-occamod");

        BlockPos turned = chairRow.east(4);
        double closeX = turned.getX() + 0.5;
        double closeY = turned.getY() + 1;
        double closeZ = turned.getZ() + 2.3;
        this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), closeX, closeY, closeZ, Set.of(), 180, 50, false));
        waitUntil("the camera faces the chair turned by 45°",
                client -> client.player.position().distanceToSqr(closeX, closeY, closeZ) < 0.01);
        screenshot("shapes-chair-outline");
    }

    private static void check(String side, BlockGetter level, Placed block) {
        VoxelShape expected = VoxelShaper.rotate(NORTH_SHAPES.get(block.mode()), Direction.NORTH, block.facing());
        VoxelShape outline = block.state().getShape(level, block.pos(), CollisionContext.empty());
        VoxelShape collision = block.state().getCollisionShape(level, block.pos(), CollisionContext.empty());
        requireSame(side + " outline of " + block.state(), expected, outline);
        requireSame(side + " collision of " + block.state(), expected, collision);
    }

    /**
     * Quarter turns of the chair are its unturned shape rotated exactly, the way blockstate y turns a
     * model; the steps between them are voxelized, differ from both and stay within the circle the chair turns in, a
     * pixel past the cell at 22.5�.
     */
    private static void checkChair(String side, BlockGetter level, BlockPos row) {
        List<VoxelShape> outlines = new ArrayList<>();
        for (int rotation = 0; rotation < 16; rotation++) {
            BlockState state = TestBlocks.TEST_CHAIR_BLOCK.defaultBlockState().setValue(TestChairBlock.ROTATION, rotation);
            BlockPos pos = row.east(2 * TestBlocks.TEST_CHAIR_BLOCK.getStateDefinition().getPossibleStates().indexOf(state));
            require(level.getBlockState(pos) == state, side + " has " + level.getBlockState(pos) + " at " + pos);
            VoxelShape outline = state.getShape(level, pos, CollisionContext.empty());
            require(!outline.isEmpty(), side + " outline of " + state + " is empty");
            requireSame(side + " collision of " + state, outline, state.getCollisionShape(level, pos, CollisionContext.empty()));
            outlines.add(outline);
        }
        Direction[] quarters = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int quarter = 1; quarter < 4; quarter++) {
            requireSame(side + " chair outline at rotation " + 4 * quarter,
                    VoxelShaper.rotate(outlines.getFirst(), Direction.NORTH, quarters[quarter]), outlines.get(4 * quarter));
        }
        AABB footprint = outlines.getFirst().bounds();
        double reach = Math.max(Math.hypot(footprint.minX - 0.5, footprint.minZ - 0.5),
                Math.hypot(footprint.maxX - 0.5, footprint.maxZ - 0.5)) + 1 / 32.0;
        for (int rotation = 1; rotation < 16; rotation += 2) {
            AABB bounds = outlines.get(rotation).bounds();
            require(bounds.minX >= 0.5 - reach && bounds.minZ >= 0.5 - reach
                            && bounds.maxX <= 0.5 + reach && bounds.maxZ <= 0.5 + reach,
                    side + " chair outline at rotation " + rotation + " reaches past its turning circle: " + bounds);
            require(volume(Shapes.join(outlines.get(rotation), outlines.get(rotation - 1), BooleanOp.NOT_SAME)) > 1e-4,
                    side + " chair outline at rotation " + rotation + " is the one at " + (rotation - 1));
        }
    }

    /**
     * The stove's collision comes from its shape model and differs from its outline; both follow the
     * facing, and lighting it changes neither.
     */
    private static void checkStove(String side, BlockGetter level, BlockPos row) {
        List<BlockState> states = TestBlocks.TEST_STOVE_BLOCK.getStateDefinition().getPossibleStates();
        BlockState north = TestBlocks.TEST_STOVE_BLOCK.defaultBlockState();
        BlockPos northPos = row.east(2 * states.indexOf(north));
        VoxelShape northOutline = north.getShape(level, northPos, CollisionContext.empty());
        VoxelShape northCollision = north.getCollisionShape(level, northPos, CollisionContext.empty());
        require(volume(Shapes.join(northOutline, northCollision, BooleanOp.NOT_SAME)) > 1e-4,
                side + " stove collision is its outline " + northOutline.toAabbs());
        for (int i = 0; i < states.size(); i++) {
            BlockState state = states.get(i);
            BlockPos pos = row.east(2 * i);
            Direction facing = state.getValue(TestStoveBlock.FACING);
            requireSame(side + " outline of " + state, VoxelShaper.rotate(northOutline, Direction.NORTH, facing),
                    state.getShape(level, pos, CollisionContext.empty()));
            requireSame(side + " collision of " + state, VoxelShaper.rotate(northCollision, Direction.NORTH, facing),
                    state.getCollisionShape(level, pos, CollisionContext.empty()));
        }
    }

    /**
     * The shapes enclose the same volume, give or take float rounding: a model's coordinates off the
     * binary grid land a few ulps apart once turned.
     */
    private static void requireSame(String what, VoxelShape expected, VoxelShape actual) {
        double difference = volume(Shapes.join(expected, actual, BooleanOp.NOT_SAME));
        require(difference < 1e-6, what + " differs by " + difference + ": it is " + actual.toAabbs()
                + ", expected " + expected.toAabbs());
    }

    private static double volume(VoxelShape shape) {
        double volume = 0;
        for (AABB box : shape.toAabbs()) volume += box.getXsize() * box.getYsize() * box.getZsize();
        return volume;
    }

    private record Placed(BlockPos pos, BlockState state, int mode, Direction facing) {
    }
}
