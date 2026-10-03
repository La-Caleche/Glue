package fr.lacaleche.glue.testmod.gametest;

import com.mojang.serialization.DataResult;
import fr.lacaleche.glue.composite.CompositeBlockEntity;
import fr.lacaleche.glue.composite.CompositeCells;
import fr.lacaleche.glue.composite.CompositePart;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.internal.GlueBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Composite cells hold several transformed blocks at one position: the server builds them, refuses
 * parts that cannot be held, reverts a cell to a plain block or air when it no longer needs to be
 * one, and the client receives the parts, their shapes and their mesh.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Fabric discovers runTest, not JUnit annotations.
public final class CompositeClientTest extends WorldClientTest {

    /** Stone at half size in the lower north-west corner. */
    private static final CompositePart CORNER = part(Blocks.STONE.defaultBlockState(),
            new Vector3f(-0.25f, -0.25f, -0.25f), 0, 0.5f);
    /** Planks turned 45° in the lower south-east quarter, voxelized. */
    private static final CompositePart TURNED = part(Blocks.OAK_PLANKS.defaultBlockState(),
            new Vector3f(0.25f, -0.25f, 0.25f), 45, 0.35f);
    /** Translucent glass at half size on top. */
    private static final CompositePart GLASS = part(Blocks.RED_STAINED_GLASS.defaultBlockState(),
            new Vector3f(0, 0.25f, 0), 0, 0.5f);
    /** A flower: an outline but no collision. */
    private static final CompositePart FLOWER = part(Blocks.POPPY.defaultBlockState(),
            new Vector3f(-0.25f, -0.25f, 0.25f), 0, 0.5f);

    private static final List<CompositePart> CELL = List.of(CORNER, TURNED, GLASS, FLOWER);

    @Override
    protected void test() {
        BlockPos cell = this.world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().blockPosition().above(2).north(3));
        BlockPos plain = cell.east(2);

        this.world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (CompositePart part : CELL) requireSuccess("adding " + part.state(), CompositeCells.add(level, cell, part));
            check("server", level, cell);

            requireError("a chest", CompositeCells.add(level, cell, new CompositePart(Blocks.CHEST.defaultBlockState())));
            requireError("a part leaving the cell", CompositeCells.add(level, cell,
                    part(Blocks.STONE.defaultBlockState(), new Vector3f(0.5f, 0, 0), 0, 1)));
            requireError("a 33rd part", CompositeCells.set(level, plain,
                    Collections.nCopies(CompositeCells.MAX_PARTS + 1, CORNER)));
            require(sameParts(CompositeCells.parts(level, cell), CELL), "refused parts changed the cell");

            requireSuccess("a plain block", CompositeCells.set(level, plain, List.of(new CompositePart(Blocks.STONE.defaultBlockState()))));
            require(level.getBlockState(plain).is(Blocks.STONE), "one untransformed part is " + level.getBlockState(plain));
            requireSuccess("a part on a plain block", CompositeCells.add(level, plain, CORNER));
            require(level.getBlockState(plain).is(GlueBlocks.COMPOSITE), "two parts are " + level.getBlockState(plain));
            requireSuccess("removing the corner", CompositeCells.remove(level, plain, 1));
            require(level.getBlockState(plain).is(Blocks.STONE), "the remaining part is " + level.getBlockState(plain));
            requireSuccess("clearing", CompositeCells.set(level, plain, List.of()));
            require(level.getBlockState(plain).isAir(), "an empty cell is " + level.getBlockState(plain));

            Tag encoded = CompositePart.CODEC.encodeStart(NbtOps.INSTANCE, TURNED).getOrThrow();
            require(sameParts(List.of(CompositePart.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow()), List.of(TURNED)),
                    "a part does not survive its codec: " + encoded);
            CompoundTag missing = ((CompoundTag) encoded).copy();
            missing.getCompoundOrEmpty("state").putString("Name", "glue-test:no_such_block");
            require(CompositePart.CODEC.parse(NbtOps.INSTANCE, missing).isError(), "a part of a missing block decodes");
        });

        waitUntil("the cell reaches the client", client -> client.level != null
                && client.level.getBlockEntity(cell) instanceof CompositeBlockEntity entity
                && entity.parts().size() == CELL.size());
        this.context.runOnClient(client -> check("client", client.level, cell));

        double x = cell.getX() + 0.5;
        double y = cell.getY() + 0.9;
        double z = cell.getZ() + 1.9;
        this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), x, y, z, Set.of(), 180, 55, false));
        waitUntil("the camera faces the cell", client -> client.player.position().distanceToSqr(x, y, z) < 0.01);
        this.context.waitTicks(20);
        screenshot("composite-cell");
    }

    private static void check(String side, BlockGetter level, BlockPos pos) {
        require(level.getBlockState(pos).is(GlueBlocks.COMPOSITE), side + " has " + level.getBlockState(pos) + " at the cell");
        require(sameParts(CompositeCells.parts(level, pos), CELL), side + " parts are " + CompositeCells.parts(level, pos));

        VoxelShape outline = Shapes.empty();
        VoxelShape collision = Shapes.empty();
        for (CompositePart part : CELL) {
            outline = Shapes.or(outline, part.outline());
            collision = Shapes.or(collision, part.collision());
        }
        requireSame(side + " cell outline", outline, level.getBlockState(pos).getShape(level, pos, CollisionContext.empty()));
        requireSame(side + " cell collision", collision,
                level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()));
        requireSame(side + " corner part", Block.box(0, 0, 0, 8, 8, 8), CORNER.outline());
        require(FLOWER.collision().isEmpty() && !FLOWER.outline().isEmpty(), side + " flower shapes are wrong");
        require(volume(TURNED.outline()) > 0 && TURNED.outline().toAabbs().size() > 1,
                side + " turned planks are not voxelized: " + TURNED.outline().toAabbs());
    }

    private static CompositePart part(BlockState state, Vector3f translation,
                                      float yaw, float scale) {
        return new CompositePart(state, new TransformationComponent(translation,
                new Quaternionf().rotationY((float) Math.toRadians(-yaw)), new Vector3f(scale), new Quaternionf()));
    }

    /**
     * The same blocks placed the same way. Records would compare float bits, and NBT turns a zero
     * quaternion component's negative sign positive.
     */
    private static boolean sameParts(List<CompositePart> actual, List<CompositePart> expected) {
        if (actual.size() != expected.size()) return false;
        for (int i = 0; i < actual.size(); i++) {
            if (actual.get(i).state() != expected.get(i).state()) return false;
            if (!actual.get(i).matrix().equals(expected.get(i).matrix(), 1e-6f)) return false;
        }
        return true;
    }

    private static void requireSuccess(String what, DataResult<List<CompositePart>> result) {
        result.ifError(error -> {
            throw new AssertionError(what + " was refused: " + error.message());
        });
    }

    private static void requireError(String what, DataResult<List<CompositePart>> result) {
        require(result.isError(), what + " was accepted");
    }
}
