package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import fr.lacaleche.glue.testmod.render.TestShaderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Stateful block entity for {@link TestShaderBlock}: tracks the tick count for
 * animation plus a persisted, client-synced shader index that the renderer uses
 * to pick a {@link TestShaderPipelines} pipeline. (Stateless demos use the shared
 * {@link TickingBlockEntity} instead.)
 */
public class TestShaderBlockEntity extends BlockEntity {

    private int ticks = 0;
    private int shaderIndex = 0;

    public TestShaderBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.SHADER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestShaderBlockEntity entity) {
        entity.ticks++;
    }

    public int getTicks() {
        return ticks;
    }

    public int getShaderIndex() {
        return shaderIndex;
    }

    public void cycleShader() {
        shaderIndex = (shaderIndex + 1) % TestShaderPipelines.count();
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
        shaderIndex = input.getIntOr("ShaderIndex", 0) % TestShaderPipelines.count();
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
