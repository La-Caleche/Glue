package fr.lacaleche.glue.client.render.model;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Predicate;

/**
 * A baked block model drawn moved by a fixed matrix, in the chunk mesh like any block: no block
 * entity, no per-frame cost. See {@link GlueBlockModels#transform}.
 *
 * <p>Only Fabric's renderer path ({@code emitQuads}) is moved. Code reading the model's vanilla
 * parts directly gets them unmoved.</p>
 */
public final class TransformedBlockModel extends WrapperBlockStateModel {

    private final Matrix4f matrix;

    public TransformedBlockModel(BlockStateModel wrapped, Matrix4fc matrix) {
        super(wrapped);
        this.matrix = new Matrix4f(matrix);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<@Nullable Direction> cullTest) {
        emitter.pushTransform(new MatrixQuadTransform(this.matrix));
        // A moved face no longer lines up with the neighbour it would be culled against.
        this.wrapped.emitQuads(emitter, blockView, pos, state, random, face -> false);
        emitter.popTransform();
    }
}
