package fr.lacaleche.glue.testmod.gametest;

import com.mojang.serialization.DataResult;
import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositeBlocks;
import fr.lacaleche.composite.CompositeCells;
import fr.lacaleche.composite.CompositePart;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlockEntity;
import fr.lacaleche.glue.testmod.registries.TestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

    /** A chest at half size, turned, in the north-west quarter. */
    private static final CompositePart CHEST = part(Blocks.CHEST.defaultBlockState(),
            new Vector3f(-0.25f, -0.25f, -0.25f), 30, 0.45f);
    /** A furnace at half size, turned, in the north-east quarter. */
    private static final CompositePart FURNACE = part(Blocks.FURNACE.defaultBlockState(),
            new Vector3f(0.25f, -0.25f, -0.25f), -20, 0.45f);
    /** A floor lever in the south-east quarter. */
    private static final CompositePart LEVER = part(Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR),
            new Vector3f(0.25f, 0, 0.25f), 0, 0.5f);
    /** The additive sprite, ticked on the client, in the south-west quarter. */
    private static final CompositePart SPRITE = part(TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK.defaultBlockState(),
            new Vector3f(-0.25f, -0.25f, 0.25f), 0, 0.45f);

    @Override
    protected void test() {
        BlockPos cell = this.world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().blockPosition().above(2).north(3));
        BlockPos plain = cell.east(2);

        this.world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (CompositePart part : CELL) requireSuccess("adding " + part.state(), CompositeCells.add(level, cell, part));
            check("server", level, cell);

            requireError("a part reaching two blocks past the cell", CompositeCells.add(level, cell,
                    part(Blocks.STONE.defaultBlockState(), new Vector3f(1.5f, 0, 0), 0, 1)));
            requireError("a 33rd part", CompositeCells.set(level, plain,
                    Collections.nCopies(CompositeCells.MAX_PARTS + 1, CORNER)));
            require(sameParts(CompositeCells.parts(level, cell), CELL), "refused parts changed the cell");

            requireSuccess("a plain block", CompositeCells.set(level, plain, List.of(new CompositePart(Blocks.STONE.defaultBlockState()))));
            require(level.getBlockState(plain).is(Blocks.STONE), "one untransformed part is " + level.getBlockState(plain));
            requireSuccess("a part on a plain block", CompositeCells.add(level, plain, CORNER));
            require(level.getBlockState(plain).is(CompositeBlocks.COMPOSITE), "two parts are " + level.getBlockState(plain));
            requireSuccess("removing the corner", CompositeCells.remove(level, plain, 1));
            require(level.getBlockState(plain).is(Blocks.STONE), "the remaining part is " + level.getBlockState(plain));
            requireSuccess("clearing", CompositeCells.set(level, plain, List.of()));

            requireSuccess("a part reaching half a block past the cell", CompositeCells.set(level, plain,
                    List.of(part(Blocks.STONE.defaultBlockState(), new Vector3f(0.5f, 0, 0), 0, 1))));
            double reach = level.getBlockState(plain).getShape(level, plain, CollisionContext.empty()).bounds().maxX;
            require(Math.abs(reach - 1.5) < 1e-6, "the reaching part's outline ends at " + reach);
            requireSuccess("clearing the reaching part", CompositeCells.set(level, plain, List.of()));
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

        blockEntities(cell.west(3));
    }

    /**
     * Parts keep their block entities: saved, carried between a plain block and a cell, ticked with
     * their own block's state standing at the cell, handed interactions, and drawn by their renderers.
     */
    private void blockEntities(BlockPos pos) {
        BlockPos plain = pos.west(2);
        this.world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            level.setBlock(plain, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            ((ChestBlockEntity) level.getBlockEntity(plain)).setItem(0, new ItemStack(Items.DIAMOND));
            requireSuccess("a part on a chest", CompositeCells.add(level, plain, CORNER));
            CompositeBlockEntity chestCell = (CompositeBlockEntity) level.getBlockEntity(plain);
            require(diamond(chestCell.entity(0)), "the chest's items did not move into its part: " + chestCell.entity(0));
            requireSuccess("removing the corner", CompositeCells.remove(level, plain, 1));
            require(diamond(level.getBlockEntity(plain)), "the chest part's items did not move back into the chest");
            require(level.getEntitiesOfClass(ItemEntity.class, new AABB(plain).inflate(2)).isEmpty(), "moving the chest dropped items");
            level.setBlock(plain, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(plain).inflate(2)).forEach(Entity::discard);

            requireSuccess("block entity parts", CompositeCells.set(level, pos, List.of(CHEST, FURNACE, LEVER, SPRITE)));
            CompositeBlockEntity cell = (CompositeBlockEntity) level.getBlockEntity(pos);
            ((ChestBlockEntity) cell.entity(0)).setItem(0, new ItemStack(Items.DIAMOND));
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) cell.entity(1);
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
            require(cell.entity(2) == null, "a lever part has a block entity");

            CompositeBlockEntity loaded = (CompositeBlockEntity) BlockEntity.loadStatic(pos, cell.getBlockState(),
                    cell.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            require(loaded != null && diamond(loaded.entity(0)), "a part's block entity does not survive saving");

            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            Vec3 eye = new Vec3(pos.getX() + 0.75, pos.getY() + 2.2, pos.getZ() + 0.25);
            Vec3 lever = new Vec3(pos.getX() + 0.75, pos.getY() + 0.05, pos.getZ() + 0.75);
            aim(player, level, eye, lever);
            BlockHitResult hit = new BlockHitResult(lever, Direction.UP, pos, false);
            InteractionResult used = level.getBlockState(pos).useWithoutItem(level, player, hit);
            require(used.consumesAction(), "pulling the lever part gave " + used);
            require(cell.parts().get(2).state().getValue(LeverBlock.POWERED), "the lever part was not pulled: " + cell.parts().get(2));
            require(level.getBlockState(pos).is(CompositeBlocks.COMPOSITE), "pulling the lever part replaced the cell");
        });

        waitUntil("the furnace part lights on the client", client -> client.level.getBlockEntity(pos) instanceof CompositeBlockEntity cell
                && cell.parts().size() == 4 && cell.parts().get(1).state().getValue(AbstractFurnaceBlock.LIT)
                && cell.entity(0) instanceof ChestBlockEntity && cell.entity(3) instanceof TestAdditiveSpriteBlockEntity);
        this.world.getServer().runOnServer(server -> require(server.overworld().getBlockState(pos).is(CompositeBlocks.COMPOSITE),
                "the furnace replaced the cell as it lit"));

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.9;
        double z = pos.getZ() + 2.2;
        this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .teleportTo(server.overworld(), x, y, z, Set.of(), 180, 35, false));
        waitUntil("the camera faces the block entity cell", client -> client.player.position().distanceToSqr(x, y, z) < 0.01);
        this.context.waitTicks(20);
        screenshot("composite-block-entities");
        this.world.getServer().runOnServer(server -> CompositeCells.set(server.overworld(), pos, List.of()));
    }

    private static boolean diamond(BlockEntity entity) {
        return entity instanceof ChestBlockEntity chest && chest.getItem(0).is(Items.DIAMOND);
    }

    /** Stands a player's eye at {@code eye}, looking at {@code target}. */
    private static void aim(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 target) {
        Vec3 view = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-view.x, view.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(view.y, view.horizontalDistance()));
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), yaw, pitch, false);
    }

    private static void check(String side, BlockGetter level, BlockPos pos) {
        require(level.getBlockState(pos).is(CompositeBlocks.COMPOSITE), side + " has " + level.getBlockState(pos) + " at the cell");
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
