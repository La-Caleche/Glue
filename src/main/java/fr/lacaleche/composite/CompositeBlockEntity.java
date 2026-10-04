package fr.lacaleche.composite;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parts of a composite cell, the block entities of block parts that have one, and the shapes
 * they add up to.
 *
 * <p>The shapes are rebuilt when the parts change, on whichever side receives them, so collision
 * never recomputes per tick. The parts are the render data the chunk mesher reads.</p>
 *
 * <p>A block part's block entity only holds what its renderer draws: it is saved and sent with the
 * cell, but never ticked, used or told of its neighbours, and goes with its part without side
 * effects, so a chest part's items go with it.</p>
 */
public class CompositeBlockEntity extends BlockEntity implements RenderDataBlockEntity {

    private List<CompositePart> parts = List.of();
    private final List<@Nullable BlockEntity> entities = new ArrayList<>();
    private List<PlacedGeometry> geometry = List.of();
    private VoxelShape outline = Shapes.empty();
    private VoxelShape collision = Shapes.empty();

    public CompositeBlockEntity(BlockEntityType<CompositeBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        List<CompositePart> parts = new ArrayList<>();
        List<@Nullable BlockEntity> entities = new ArrayList<>();
        for (ValueInput entry : input.childrenListOrEmpty("parts")) {
            if (parts.size() == CompositeCells.MAX_PARTS) break;
            // A part whose block or item is gone fails to decode and is skipped, reported by the input.
            TransformationComponent transform = entry.read("transform", TransformationComponent.CODEC).orElse(TransformationComponent.DEFAULT);
            Optional<ItemStack> item = entry.read("item", ItemStack.CODEC);
            if (item.isPresent()) {
                parts.add(new ItemPart(item.get(), transform));
                entities.add(null);
                continue;
            }
            Optional<BlockState> state = entry.read("state", BlockState.CODEC);
            if (state.isEmpty()) continue;

            BlockEntity entity = reusedEntity(parts.size(), state.get());
            if (entity != null) entry.child("entity").ifPresent(entity::loadWithComponents);
            parts.add(new BlockPart(state.get(), transform));
            entities.add(entity);
        }
        replace(parts, entities);
        if (this.level != null && this.level.isClientSide()) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("parts");
        for (int i = 0; i < this.parts.size(); i++) {
            ValueOutput entry = list.addChild();
            store(entry, this.parts.get(i));
            BlockEntity entity = this.entities.get(i);
            if (entity != null) entity.saveWithoutMetadata(entry.child("entity"));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The parts, each block part with what its own block entity sends to clients. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), Composite.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ValueOutput.ValueOutputList list = output.childrenList("parts");
            for (int i = 0; i < this.parts.size(); i++) {
                ValueOutput entry = list.addChild();
                store(entry, this.parts.get(i));
                BlockEntity entity = this.entities.get(i);
                if (entity != null) entry.store("entity", CompoundTag.CODEC, entity.getUpdateTag(registries));
            }
            return output.buildResult();
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        for (BlockEntity entity : this.entities) {
            if (entity != null) entity.setLevel(level);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (BlockEntity entity : this.entities) {
            if (entity != null) entity.setRemoved();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        for (BlockEntity entity : this.entities) {
            if (entity != null) entity.clearRemoved();
        }
    }

    /** The parts, as the mesher draws them: an immutable {@code List<CompositePart>}. */
    @Override
    public Object getRenderData() {
        return this.parts;
    }

    /** The parts, in drawing order. The list is immutable. */
    public List<CompositePart> parts() {
        return this.parts;
    }

    /** The block entity of the part at an index, or {@code null} for an item part or a block without one. */
    public @Nullable BlockEntity entity(int index) {
        return this.entities.get(index);
    }

    /** Every part's geometry, in part order. The list is immutable. */
    public List<PlacedGeometry> geometry() {
        return this.geometry;
    }

    public VoxelShape outline() {
        return this.outline;
    }

    public VoxelShape collision() {
        return this.collision;
    }

    /**
     * Replaces the parts and sends them to the clients tracking the cell. Server side. A part kept
     * from the current list, the same instance, keeps its block entity; a new block part gets a new
     * one, empty.
     */
    void setParts(List<CompositePart> parts) {
        boolean[] kept = new boolean[this.parts.size()];
        List<@Nullable BlockEntity> entities = new ArrayList<>();
        for (CompositePart part : parts) {
            int current = indexOf(part, kept);
            if (current >= 0) kept[current] = true;
            entities.add(current >= 0 ? this.entities.get(current) : newEntity(part));
        }
        replace(parts, entities);
        setChanged();
        if (this.level != null) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The current block entity at an index when it still fits a state, a new one otherwise. */
    @SuppressWarnings("deprecation") // Vanilla's chunks keep a block entity through a state of its block the same way.
    private @Nullable BlockEntity reusedEntity(int index, BlockState state) {
        BlockEntity current = index < this.entities.size() ? this.entities.get(index) : null;
        if (current != null && current.getBlockState().is(state.getBlock())) {
            current.setBlockState(state);
            return current;
        }
        return newEntity(new BlockPart(state));
    }

    private @Nullable BlockEntity newEntity(CompositePart part) {
        if (!(part instanceof BlockPart block) || !(block.state().getBlock() instanceof EntityBlock entityBlock)) return null;
        BlockEntity entity = entityBlock.newBlockEntity(this.worldPosition, block.state());
        if (entity != null && this.level != null) entity.setLevel(this.level);
        return entity;
    }

    /** The first index holding this very part and not yet taken, or {@code -1}. */
    private int indexOf(CompositePart part, boolean[] taken) {
        for (int i = 0; i < this.parts.size(); i++) {
            if (this.parts.get(i) == part && !taken[i]) return i;
        }
        return -1;
    }

    private void replace(List<CompositePart> parts, List<@Nullable BlockEntity> entities) {
        for (BlockEntity entity : this.entities) {
            if (entity != null && !containsInstance(entities, entity)) entity.setRemoved();
        }
        this.parts = List.copyOf(parts);
        this.entities.clear();
        this.entities.addAll(entities);

        List<PlacedGeometry> geometry = new ArrayList<>();
        VoxelShape collision = Shapes.empty();
        for (CompositePart part : this.parts) {
            geometry.addAll(part.geometry());
            collision = Shapes.or(collision, part.collision());
        }
        this.geometry = List.copyOf(geometry);
        this.outline = PlacedGeometry.toShape(this.geometry, ShapeGeometry.DEFAULT_RESOLUTION);
        this.collision = collision.optimize();
    }

    /** A part's fields, as {@link CompositePart#CODEC} writes them. */
    private static void store(ValueOutput output, CompositePart part) {
        switch (part) {
            case BlockPart block -> output.store("state", BlockState.CODEC, block.state());
            case ItemPart item -> output.store("item", ItemStack.CODEC, item.stack());
        }
        if (!part.transform().equals(TransformationComponent.DEFAULT)) {
            output.store("transform", TransformationComponent.CODEC, part.transform());
        }
    }

    private static boolean containsInstance(List<@Nullable BlockEntity> entities, BlockEntity entity) {
        for (BlockEntity candidate : entities) {
            if (candidate == entity) return true;
        }
        return false;
    }
}
