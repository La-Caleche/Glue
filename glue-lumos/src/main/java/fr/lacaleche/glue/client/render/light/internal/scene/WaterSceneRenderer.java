package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Re-rasterises nearby water surfaces from the camera's point of view into the shared material
 * G-buffer under the {@code WATER} material id (5), so the deferred and reflection passes can
 * identify visible water by material id rather than a depth heuristic &mdash; the same contract
 * {@link GlassSceneRenderer} uses for glass.
 *
 * <p>Water is a fluid, not a block model: it is drawn by {@link BlockRenderDispatcher#renderLiquid}
 * (which takes no {@code PoseStack} and writes vertices in <em>section-local</em> coordinates,
 * {@code blockPos & 15}), so the glass approach of baking a per-block translate into a pose stack
 * does not transfer. Instead each fluid draw is routed through {@link OffsetVertexConsumer}, which
 * adds {@code sectionOrigin - camera} to every vertex, producing the camera-relative world
 * coordinates the pipeline's model-view expects.</p>
 *
 * <p>The block list comes from {@link MaterialBlockScan}, cached per light and already reduced to
 * fluid blocks with a potentially visible face. Submerged blocks are dropped there rather than left to
 * {@code renderLiquid}: it does reject them, but only after allocating the six neighbour positions it
 * needs to prove it, which would be paid on every one of them every frame.</p>
 */
@Environment(EnvType.CLIENT)
public final class WaterSceneRenderer extends AbstractSceneRenderer {

    private final OffsetVertexConsumer offset = new OffsetVertexConsumer();
    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<BlockPos> blocks = List.of();

    public void configure(Matrix4f view, Matrix4f proj,
                          double camX, double camY, double camZ, List<BlockPos> blocks) {
        this.view = view;
        this.proj = proj;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.blocks = blocks;
    }

    @Override
    protected Matrix4f createProjectionMatrix(int width, int height) {
        return proj;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return view;
    }

    @Override
    protected float getScale() {
        return 1.0f;
    }

    @Override
    protected PoseStack buildModelViewMatrix(Matrix4f viewMatrix, float scale) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        return matrices;
    }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null || blocks.isEmpty()) return;
        if (!GBufferCapture.beginWaterCapture()) return;
        try {
            RenderType type = ShadowPipelines.waterGBuffer();
            BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
            MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
            VertexConsumer buffer = bufferSource.getBuffer(type);

            for (BlockPos pos : blocks) {
                BlockState state = client.level.getBlockState(pos);
                FluidState fluid = state.getFluidState();
                if (fluid.isEmpty()) continue;
                // renderLiquid writes section-local coordinates; shift them to camera-relative world.
                offset.set(buffer,
                        (float) ((pos.getX() & ~15) - camX),
                        (float) ((pos.getY() & ~15) - camY),
                        (float) ((pos.getZ() & ~15) - camZ));
                blockRenderer.renderLiquid(pos, client.level, offset, state, fluid);
            }

            bufferSource.endBatch(type);
        } finally {
            GBufferCapture.endWaterCapture();
        }
    }

    /**
     * A {@link VertexConsumer} that offsets every position by a per-block amount before delegating.
     * {@code renderLiquid} emits section-local coordinates and no transform of its own; adding
     * {@code sectionOrigin - camera} yields the camera-relative world coordinates the pipeline expects.
     * {@code addVertex} returns the delegate, so the chained {@code setColor/setUv/setLight/setNormal}
     * calls bypass this wrapper untouched.
     */
    private static final class OffsetVertexConsumer implements VertexConsumer {
        private VertexConsumer delegate;
        private float ox, oy, oz;

        void set(VertexConsumer delegate, float ox, float oy, float oz) {
            this.delegate = delegate;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x + ox, y + oy, z + oz);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return delegate.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return delegate.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return delegate.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return delegate.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return delegate.setNormal(x, y, z);
        }
    }
}
