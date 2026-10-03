package fr.lacaleche.glue.composite;

import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts of a composite cell and the shapes they add up to.
 *
 * <p>The shapes are rebuilt when the parts change, on whichever side receives them, so collision
 * never recomputes per tick. The parts are the render data the chunk mesher reads.</p>
 */
public class CompositeBlockEntity extends BlockEntity implements RenderDataBlockEntity {

    private List<CompositePart> parts = List.of();
    private List<PlacedGeometry> geometry = List.of();
    private VoxelShape outline = Shapes.empty();
    private VoxelShape collision = Shapes.empty();

    public CompositeBlockEntity(BlockEntityType<CompositeBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Parts whose block is gone fail to decode and are skipped, reported by the input.
        update(input.listOrEmpty("parts", CompositePart.CODEC).stream().limit(CompositeCells.MAX_PARTS).toList());
        if (this.level != null && this.level.isClientSide()) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.TypedOutputList<CompositePart> list = output.list("parts", CompositePart.CODEC);
        this.parts.forEach(list::add);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
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

    /** Replaces the parts and sends them to the clients tracking the cell. Server side. */
    void setParts(List<CompositePart> parts) {
        update(parts);
        setChanged();
        if (this.level != null) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void update(List<CompositePart> parts) {
        this.parts = List.copyOf(parts);
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
}
