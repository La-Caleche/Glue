package fr.lacaleche.glue.registries;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

public class BlockEntitiesRegistry extends GlueRegistry {

    public BlockEntitiesRegistry(String modId) {
        super(modId);
    }

    public BlockEntitiesRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public <T extends BlockEntityType<?>> T register(String path, T blockEntityType) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.id(path), blockEntityType);
    }

    /**
     * Builds and registers a block-entity type whose factory receives the type itself, so entities
     * that pass their own type to the {@link BlockEntity} constructor need no self-referential
     * supplier dance:
     * <pre>{@code
     * public static final BlockEntityType<MyBlockEntity> MY_BE =
     *         REGISTRY.register("my_be", MyBlockEntity::new, MY_BLOCK);
     * }</pre>
     */
    public <T extends BlockEntity> BlockEntityType<T> register(String path, Factory<T> factory, Block... blocks) {
        SelfReferencingFactory<T> selfReferencing = new SelfReferencingFactory<>(factory);
        BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(selfReferencing, blocks).build();
        selfReferencing.type = type;
        return register(path, type);
    }

    /**
     * Creates a block entity for the given position and state. Receives the {@link BlockEntityType}
     * the entity belongs to — the one the enclosing registration created.
     */
    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {

        T create(BlockEntityType<T> type, BlockPos pos, BlockState state);
    }

    /**
     * Bridges the circular dependency between a type and its factory: the type is constructed
     * around this supplier, and {@link #type} is assigned before the registration call returns —
     * long before any block entity can be created.
     */
    private static final class SelfReferencingFactory<T extends BlockEntity>
            implements FabricBlockEntityTypeBuilder.Factory<T> {

        private final Factory<T> factory;
        private BlockEntityType<T> type;

        private SelfReferencingFactory(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        public T create(BlockPos pos, BlockState state) {
            return factory.create(type, pos, state);
        }
    }
}
