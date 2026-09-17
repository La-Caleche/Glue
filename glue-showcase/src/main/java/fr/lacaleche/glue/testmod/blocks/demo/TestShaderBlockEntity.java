package fr.lacaleche.glue.testmod.blocks.demo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Stateful block entity for {@link TestShaderBlock}: the shared {@link TickingBlockEntity} tick
 * clock plus a persisted, client-synced shader index. The index is deliberately a plain unbounded
 * counter &mdash; this class loads on dedicated servers, so only client-side code resolves it
 * (modulo the pipeline count) against the client-only
 * {@code fr.lacaleche.glue.testmod.render.TestShaderPipelines} registry.
 */
public class TestShaderBlockEntity extends TickingBlockEntity {

    private int shaderIndex = 0;

    public TestShaderBlockEntity(BlockEntityType<TestShaderBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getShaderIndex() {
        return shaderIndex;
    }

    public void cycleShader() {
        shaderIndex++;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ShaderIndex", shaderIndex);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        shaderIndex = Math.max(0, input.getIntOr("ShaderIndex", 0));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
