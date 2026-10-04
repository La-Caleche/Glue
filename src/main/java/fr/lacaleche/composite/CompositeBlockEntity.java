package fr.lacaleche.composite;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.GeometryHit;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The parts of a composite cell, the block entities of those that have one, and the shapes they add
 * up to.
 *
 * <p>The shapes are rebuilt when the parts change, on whichever side receives them, so collision
 * never recomputes per tick. The parts are the render data the chunk mesher reads.</p>
 *
 * <p>A part's block entity is saved and sent with the cell, ticked by the cell's ticker, and given
 * the interactions aimed at its part. Its code runs in a {@link PartScope}, so that it reads and
 * sets its own block at the cell's position: a part whose block sets itself to air leaves the cell.
 * Scheduled ticks and block events addressed to a part's block at the cell's position reach the
 * first part of that block, and a part's menu stays open while the cell holds the part.</p>
 */
public class CompositeBlockEntity extends BlockEntity implements RenderDataBlockEntity {

    private List<CompositePart> parts = List.of();
    private final List<@Nullable BlockEntity> entities = new ArrayList<>();
    private List<List<PlacedGeometry>> partGeometry = List.of();
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
            // A part whose block is gone fails to decode and is skipped, reported by the input.
            Optional<BlockState> state = entry.read("state", BlockState.CODEC);
            if (state.isEmpty()) continue;

            BlockEntity entity = reusedEntity(parts.size(), state.get());
            if (entity != null) entry.child("entity").ifPresent(entity::loadWithComponents);
            parts.add(new CompositePart(state.get(),
                    entry.read("transform", TransformationComponent.CODEC).orElse(TransformationComponent.DEFAULT)));
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

    /** The parts, each with what its own block entity sends to clients. */
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

    /** Each part's block entity reacts to its removal along with the cell: a container drops its items. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        for (int i = 0; i < this.parts.size(); i++) removeSideEffects(i);
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

    /** The block entity of the part at an index, or {@code null} when its block has none. */
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

    /** Whether a part's block entity is this very one. */
    public boolean holds(BlockEntity entity) {
        return containsInstance(this.entities, entity);
    }

    /** Whether a part is of this block. */
    public boolean holdsPartOf(Block block) {
        return partOf(block) >= 0;
    }

    /**
     * Runs code addressed to a block at the cell's position, such as a scheduled tick or a block
     * event, for the first part of that block, in its scope.
     *
     * @return the action's result, or empty when no part is of that block
     */
    public <T> Optional<T> runPartOf(Block block, Function<PartScope, T> action) {
        int index = partOf(block);
        return index < 0 ? Optional.empty() : Optional.of(runPart(index, action));
    }

    /**
     * The index of the part a player aims at within reach, or {@code -1}: the part whose geometry
     * their view enters first.
     */
    public int targetedPart(Player player) {
        Vec3 origin = Vec3.atLowerCornerOf(this.worldPosition);
        Vec3 eye = player.getEyePosition();
        Vec3 from = eye.subtract(origin);
        Vec3 to = eye.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1)).subtract(origin);
        int nearest = -1;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < this.partGeometry.size(); i++) {
            Optional<GeometryHit> hit = PlacedGeometry.clip(this.partGeometry.get(i), from, to);
            if (hit.isEmpty()) continue;

            double distance = hit.get().location().distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    InteractionResult useItemOn(ItemStack stack, Player player, InteractionHand hand, BlockHitResult hit) {
        int index = targetedPart(player);
        if (index < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        BlockHitResult partHit = partHit(index, hit);
        return runPart(index, scope -> scope.state().useItemOn(stack, this.level, player, hand, partHit));
    }

    InteractionResult useWithoutItem(Player player, BlockHitResult hit) {
        int index = targetedPart(player);
        if (index < 0) return InteractionResult.PASS;
        BlockHitResult partHit = partHit(index, hit);
        return runPart(index, scope -> scope.state().useWithoutItem(this.level, player, partHit));
    }

    void attack(Player player) {
        int index = targetedPart(player);
        if (index < 0) return;
        runPart(index, scope -> {
            scope.state().attack(this.level, this.worldPosition, player);
            return null;
        });
    }

    /** Ticks each part's block entity as its block's ticker on this side would. */
    void tick() {
        for (int i = 0; i < this.parts.size(); i++) {
            BlockState state = this.parts.get(i).state();
            BlockEntity entity = this.entities.get(i);
            BlockEntityTicker<BlockEntity> ticker = ticker(state, entity);
            if (ticker == null) continue;

            int count = this.parts.size();
            runPart(i, scope -> {
                ticker.tick(this.level, this.worldPosition, state, entity);
                return null;
            });
            // A part that removed itself shifts the next one to this index.
            if (this.parts.size() < count) i--;
        }
    }

    /**
     * Replaces the parts and sends them to the clients tracking the cell. Server side. A part kept
     * from the current list, the same instance, keeps its block entity; a removed part's block entity
     * reacts as to its block's removal.
     */
    void setParts(List<CompositePart> parts) {
        boolean[] kept = new boolean[this.parts.size()];
        List<@Nullable BlockEntity> entities = new ArrayList<>();
        for (CompositePart part : parts) {
            int current = indexOf(part, kept);
            if (current >= 0) kept[current] = true;
            entities.add(current >= 0 ? this.entities.get(current) : PartScope.newEntity(this.level, this.worldPosition, part.state()));
        }
        for (int i = 0; i < kept.length; i++) {
            if (!kept[i]) removeSideEffects(i);
        }
        replace(parts, entities);
        changed(Block.UPDATE_CLIENTS);
    }

    private <T> T runPart(int index, Function<PartScope, T> action) {
        CompositePart part = this.parts.get(index);
        PartScope scope = PartScope.open(this.level, this.worldPosition, part.state(), this.entities.get(index));
        T result;
        try {
            result = action.apply(scope);
        } finally {
            scope.close();
        }
        if (scope.changed()) apply(part, scope);
        return result;
    }

    /** Applies the state a part's code set: a new state for the part, or its removal for air. */
    private void apply(CompositePart part, PartScope scope) {
        int index = indexOf(part, new boolean[this.parts.size()]);
        if (index < 0) return;

        List<CompositePart> parts = new ArrayList<>(this.parts);
        List<@Nullable BlockEntity> entities = new ArrayList<>(this.entities);
        if (scope.state().isAir()) {
            parts.remove(index);
            entities.remove(index);
        } else {
            parts.set(index, new CompositePart(scope.state(), part.transform()));
            entities.set(index, scope.entity());
        }
        replace(parts, entities);
        if (this.level != null && !this.level.isClientSide() && this.parts.isEmpty()) {
            this.level.removeBlock(this.worldPosition, false);
        } else {
            changed(Block.UPDATE_ALL);
        }
    }

    private void changed(int flags) {
        setChanged();
        if (this.level != null) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, flags);
        }
    }

    private void removeSideEffects(int index) {
        BlockEntity entity = this.entities.get(index);
        if (entity == null || this.level == null || this.level.isClientSide()) return;
        BlockState state = this.parts.get(index).state();
        try (PartScope ignored = PartScope.open(this.level, this.worldPosition, state, entity)) {
            entity.preRemoveSideEffects(this.worldPosition, state);
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
        return PartScope.newEntity(this.level, this.worldPosition, state);
    }

    @SuppressWarnings("unchecked")
    private @Nullable BlockEntityTicker<BlockEntity> ticker(BlockState state, @Nullable BlockEntity entity) {
        if (this.level == null || entity == null || entity.isRemoved() || !(state.getBlock() instanceof EntityBlock block)) {
            return null;
        }
        return block.getTicker(this.level, state, (BlockEntityType<BlockEntity>) entity.getType());
    }

    /** A hit on the cell as the part's block would see it: moved back by the part's transform. */
    private BlockHitResult partHit(int index, BlockHitResult hit) {
        Matrix4f inverse = this.parts.get(index).matrix().invert();
        Vec3 origin = Vec3.atLowerCornerOf(this.worldPosition);
        Vector3f local = inverse.transformPosition(hit.getLocation().subtract(origin).toVector3f());
        Direction face = Direction.rotate(inverse, hit.getDirection());
        return new BlockHitResult(new Vec3(local).add(origin), face, this.worldPosition, hit.isInside());
    }

    private int partOf(Block block) {
        for (int i = 0; i < this.parts.size(); i++) {
            if (this.parts.get(i).state().is(block)) return i;
        }
        return -1;
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

        List<List<PlacedGeometry>> partGeometry = new ArrayList<>();
        List<PlacedGeometry> geometry = new ArrayList<>();
        VoxelShape collision = Shapes.empty();
        for (CompositePart part : this.parts) {
            List<PlacedGeometry> placed = part.geometry();
            partGeometry.add(placed);
            geometry.addAll(placed);
            collision = Shapes.or(collision, part.collision());
        }
        this.partGeometry = Collections.unmodifiableList(partGeometry);
        this.geometry = List.copyOf(geometry);
        this.outline = PlacedGeometry.toShape(this.geometry, ShapeGeometry.DEFAULT_RESOLUTION);
        this.collision = collision.optimize();
    }

    /** A part's fields, as {@link CompositePart#CODEC} writes them. */
    private static void store(ValueOutput output, CompositePart part) {
        output.store("state", BlockState.CODEC, part.state());
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
