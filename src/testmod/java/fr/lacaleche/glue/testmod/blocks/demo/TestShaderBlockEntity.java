package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
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
 * Block entity providing tick-based animation data and shader selection
 * for the shader demo block renderer.
 */
public class TestShaderBlockEntity extends BlockEntity {

    /** Total number of available shader effects */
    public static final int SHADER_COUNT = 6;

    /** Animation tick counter */
    private int ticks = 0;

    /** Current shader index (0 = hologram, 1 = enchanted, 2 = frozen, 3 = xray, 4 = inferno) */
    private int shaderIndex = 0;

    public TestShaderBlockEntity(BlockPos pos, BlockState state) {
        super(TestBlockEntities.SHADER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TestShaderBlockEntity entity) {
        entity.ticks++;
    }

    public int getTicks() {
        return this.ticks;
    }

    /**
     * @return Normalized time value for smooth animations (0-1 cycling)
     */
    public float getAnimationProgress() {
        return (this.ticks % 200) / 200f;
    }

    /**
     * @return Current shader index (0..SHADER_COUNT-1)
     */
    public int getShaderIndex() {
        return shaderIndex;
    }

    /**
     * Cycle to the next shader effect and sync to clients.
     */
    public void cycleShader() {
        shaderIndex = (shaderIndex + 1) % SHADER_COUNT;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Persistence (1.21.8 ValueInput/ValueOutput API) ──────────

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ShaderIndex", shaderIndex);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        shaderIndex = input.getIntOr("ShaderIndex", 0) % SHADER_COUNT;
    }

    // ── Client sync ──────────────────────────────────────────────

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
