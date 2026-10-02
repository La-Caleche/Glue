package fr.lacaleche.glue.client.render.composite;

import fr.lacaleche.glue.composite.CompositePart;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The model of a composite cell: each part's own block model, emitted into the chunk mesh through
 * the part's transform. A cell has no block-entity renderer and costs nothing per frame.
 */
public final class CompositeBlockModel implements BlockStateModel {

    private static final Material FALLBACK_PARTICLE = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("oak_planks");

    private final TextureAtlasSprite fallbackParticle;

    private CompositeBlockModel(TextureAtlasSprite fallbackParticle) {
        this.fallbackParticle = fallbackParticle;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<@Nullable Direction> cullTest) {
        BlockModelShaper models = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        for (CompositePart part : parts(blockView, pos)) {
            BlockStateModel model = models.getBlockModel(part.state());
            boolean identity = part.isIdentity();
            emitter.pushTransform(new CompositePartTransform(part, identity));
            // A transformed part no longer lines up with its neighbours, so none of its faces is culled.
            model.emitQuads(emitter, blockView, pos, part.state(), random, identity ? cullTest : face -> false);
            emitter.popTransform();
        }
    }

    @Override
    public TextureAtlasSprite particleSprite(BlockAndTintGetter blockView, BlockPos pos, BlockState state) {
        List<CompositePart> parts = parts(blockView, pos);
        if (parts.isEmpty()) return this.fallbackParticle;
        return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(parts.getFirst().state());
    }

    /** Vanilla's part list is empty: the parts depend on the block entity, which only emitQuads sees. */
    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts) {
    }

    @Override
    public TextureAtlasSprite particleIcon() {
        return this.fallbackParticle;
    }

    private static List<CompositePart> parts(BlockAndTintGetter blockView, BlockPos pos) {
        if (!(blockView.getBlockEntityRenderData(pos) instanceof List<?> entries)) return List.of();
        return entries.stream()
                .filter(CompositePart.class::isInstance)
                .map(CompositePart.class::cast)
                .toList();
    }

    /** The same model for every state of the composite block. */
    public static final class Unbaked implements BlockStateModel.UnbakedRoot {

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            // The parts are drawn with their own blocks' models, which vanilla resolves already.
        }

        @Override
        public BlockStateModel bake(BlockState state, ModelBaker baker) {
            return new CompositeBlockModel(baker.sprites().get(FALLBACK_PARTICLE, () -> "glue:composite"));
        }

        @Override
        public Object visualEqualityGroup(BlockState state) {
            return this;
        }
    }
}
