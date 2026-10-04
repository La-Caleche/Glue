package fr.lacaleche.glue.client.render.model;

import fr.lacaleche.glue.block.Rotation16;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Block models moved by their block state, baked into the chunk mesh: a block turned in sixteen
 * steps, for instance, keeps one blockstate model and needs no block entity or renderer of its own.
 *
 * <p>Register from a client initializer, before models load:</p>
 * <pre>{@code
 * GlueBlockModels.rotation16(MyBlocks.CHAIR, BlockStateProperties.ROTATION_16);
 * }</pre>
 */
public final class GlueBlockModels {

    private static final Map<Block, Function<BlockState, Matrix4fc>> TRANSFORMS = new ConcurrentHashMap<>();

    private GlueBlockModels() {
    }

    /**
     * Draws every state of a block moved by a matrix in block units, applied after the blockstate
     * rotation. Identity matrices leave the model as it is.
     *
     * @throws IllegalStateException if the block already has a transform
     */
    public static void transform(Block block, Function<BlockState, Matrix4fc> transform) {
        if (TRANSFORMS.putIfAbsent(block, transform) != null) {
            throw new IllegalStateException(block + " already has a model transform");
        }
    }

    /**
     * Draws a block turned by a 16-step property, as {@link Rotation16#matrix} turns it; generate
     * its shapes with {@code BlockShapeProvider.Rule.rotation16} so that they turn with it.
     *
     * @throws IllegalArgumentException if the block lacks the property or it is not 0 to 15
     */
    public static void rotation16(Block block, IntegerProperty property) {
        if (!block.getStateDefinition().getProperties().contains(property)) {
            throw new IllegalArgumentException(block + " has no property " + property.getName());
        }
        Rotation16.check(property);
        transform(block, state -> Rotation16.matrix(state.getValue(property)));
    }

    /** Wraps the models of registered blocks as they bake. Called by Glue's client initializer. */
    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                (model, modelContext) -> moved(model, modelContext.state())));
    }

    private static BlockStateModel moved(BlockStateModel model, BlockState state) {
        Matrix4fc matrix = matrix(state);
        return matrix == null ? model : new TransformedBlockModel(model, matrix);
    }

    private static @Nullable Matrix4fc matrix(BlockState state) {
        Function<BlockState, Matrix4fc> transform = TRANSFORMS.get(state.getBlock());
        if (transform == null) return null;
        Matrix4fc matrix = transform.apply(state);
        return matrix.equals(new Matrix4f(), 1e-6f) ? null : matrix;
    }
}
