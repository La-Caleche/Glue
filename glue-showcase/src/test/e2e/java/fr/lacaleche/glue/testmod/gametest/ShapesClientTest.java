package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.block.GeometryRaycast;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.shaper.PlacedGeometry;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    /** Rays per side of a block in {@link #checkPicks}. */
    private static final int PICK_GRID = 32;

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
        this.context.waitTicks(5);
        this.context.runOnClient(client -> require(client.hitResult instanceof BlockHitResult hit
                        && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(turned),
                "the player targets " + describe(client.hitResult) + " instead of the chair at " + turned));
        screenshot("shapes-chair-outline");

        BlockPos stove = stoveRow.east(2 * stoves.indexOf(TestBlocks.TEST_STOVE_BLOCK.defaultBlockState()));
        Vec3 overhang = this.context.computeOnClient(client -> overhangPoint(client.level, stove));
        Vec3 eye = overhang.add(0, 0.4, 1.6);
        double eyeHeight = this.context.computeOnClient(client -> (double) client.player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-(overhang.x - eye.x), overhang.z - eye.z));
        float pitch = (float) Math.toDegrees(Math.atan2(eye.y - overhang.y, eye.distanceTo(new Vec3(overhang.x, eye.y, overhang.z))));
        this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), eye.x, eye.y - eyeHeight, eye.z, Set.of(), yaw, pitch, false));
        waitUntil("the camera faces the stove's upper half",
                client -> client.player.getEyePosition().distanceToSqr(eye) < 0.01);
        this.context.waitTicks(5);
        this.context.runOnClient(client -> require(client.hitResult instanceof BlockHitResult hit
                        && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(stove),
                "aiming above the stove targets " + describe(client.hitResult) + " instead of the stove at " + stove));
        screenshot("shapes-stove-overhang");
    }

    private static String describe(HitResult hit) {
        if (!(hit instanceof BlockHitResult block)) return String.valueOf(hit);
        return block.getType() + " " + block.getBlockPos().toShortString() + " " + block.getDirection() + " at " + block.getLocation();
    }

    /** A point of the stove's geometry in the cell above it, which only its overhang can answer for. */
    private static Vec3 overhangPoint(BlockGetter level, BlockPos stove) {
        BlockState state = level.getBlockState(stove);
        require(GeometryRaycast.overhangs(level, stove, state), "the stove's geometry stays in its cell");
        for (int i = 0; i < PICK_GRID; i++) {
            for (int j = 0; j < PICK_GRID; j++) {
                Vec3 from = Vec3.atLowerCornerOf(stove).add((i + 0.5) / PICK_GRID, 1.9, (j + 0.5) / PICK_GRID);
                BlockHitResult hit = GeometryRaycast.clipGeometry(level, stove, state, from, from.subtract(0, 0.6, 0));
                if (hit != null) return hit.getLocation().subtract(0, 0.05, 0);
            }
        }
        throw new AssertionError("no part of the stove rises into the cell above it");
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
     * model; the steps between them are voxelized, differ from both and stay within the circle the
     * chair turns in, a pixel past the cell at 22.5°. Its collision is its outline at quarter turns
     * and a coarser shape between them, and picks hit its geometry.
     */
    private static void checkChair(String side, BlockGetter level, BlockPos row) {
        List<VoxelShape> outlines = new ArrayList<>();
        for (int rotation = 0; rotation < 16; rotation++) {
            BlockState state = TestBlocks.TEST_CHAIR_BLOCK.defaultBlockState().setValue(TestChairBlock.ROTATION, rotation);
            BlockPos pos = row.east(2 * TestBlocks.TEST_CHAIR_BLOCK.getStateDefinition().getPossibleStates().indexOf(state));
            require(level.getBlockState(pos) == state, side + " has " + level.getBlockState(pos) + " at " + pos);
            VoxelShape outline = state.getShape(level, pos, CollisionContext.empty());
            require(!outline.isEmpty(), side + " outline of " + state + " is empty");
            VoxelShape collision = state.getCollisionShape(level, pos, CollisionContext.empty());
            if (rotation % 4 == 0) requireSame(side + " collision of " + state, outline, collision);
            else require(collision.toAabbs().size() < outline.toAabbs().size(),
                    side + " collision of " + state + " has " + collision.toAabbs().size() + " boxes, the outline "
                            + outline.toAabbs().size());
            checkPicks(side, level, pos, state, outline, rotation % 4 != 0);
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
     * Rays dropped on a grid over a geometry block hit within its geometry's bounds; when the block
     * is turned off the quarters, some of them pass or meet its geometry where its voxel outline
     * would not.
     */
    private static void checkPicks(String side, BlockGetter level, BlockPos pos, BlockState state, VoxelShape outline,
                                   boolean turned) {
        List<PlacedGeometry> geometry = ((GlueBlock) state.getBlock()).getGeometry(state, level, pos);
        require(geometry != null && !geometry.isEmpty(), side + " " + state + " has no geometry");
        AABB bounds = PlacedGeometry.bounds(geometry).move(pos).inflate(1e-4);
        int differing = 0;
        for (int i = 0; i < PICK_GRID; i++) {
            for (int j = 0; j < PICK_GRID; j++) {
                Vec3 from = Vec3.atLowerCornerOf(pos).add((i + 0.5) / PICK_GRID, 2, (j + 0.5) / PICK_GRID);
                Vec3 to = from.subtract(0, 3, 0);
                BlockHitResult exact = GeometryRaycast.clipGeometry(level, pos, state, from, to);
                BlockHitResult voxel = outline.clip(from, to, pos);
                if (exact != null) require(bounds.contains(exact.getLocation()),
                        side + " pick of " + state + " at " + exact.getLocation() + " is outside " + bounds);
                if (exact == null || voxel == null ? exact != voxel
                        : Math.abs(exact.getLocation().y - voxel.getLocation().y) > 1e-4) differing++;
            }
        }
        require(!turned || differing > 0, side + " picks of " + state + " all land on its voxel outline");
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

    private record Placed(BlockPos pos, BlockState state, int mode, Direction facing) {
    }
}
