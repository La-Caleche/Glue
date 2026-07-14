package fr.lacaleche.glue.client.render.internal.world;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.render.pipeline.MaterialFrame;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderFrame;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderPipeline;
import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Optional;

/**
 * Final-color frame built from Minecraft's main render target and the matrices captured for the
 * level pass. Subclasses decide when they own the frame and whether a material capture is opened.
 */
abstract class AbstractMainTargetWorldPipeline implements WorldRenderPipeline {

    private long frameSequence;
    private long matrixCaptureVersion;

    protected final void beginFrame(long sequence) {
        frameSequence = sequence;
        matrixCaptureVersion = FrameMatrices.getCaptureVersion();
    }

    @Override
    public Optional<WorldRenderFrame> currentFrame(long sequence) {
        if (sequence != frameSequence || matrixCaptureVersion != FrameMatrices.getCaptureVersion()) {
            return Optional.empty();
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f view = FrameMatrices.getView();
        Matrix4f projection = FrameMatrices.getProjection();
        if (view == null || projection == null) return Optional.empty();

        int framebuffer = FramebufferHelper.getFramebufferId(main);
        int color = FramebufferHelper.getColorTextureId(main);
        int depth = FramebufferHelper.getDepthTextureId(main);
        if (framebuffer <= 0 || color <= 0 || depth <= 0) return Optional.empty();

        Optional<MaterialFrame> material = TerrainMaterialBuffer.currentFrame(sequence);
        Vec3 position = camera.getPosition();
        return Optional.of(new WorldRenderFrame(sequence, framebuffer, framebuffer, color, depth,
                main.width, main.height, view, projection,
                new Vector3d(position.x, position.y, position.z),
                WorldRenderFrame.ColorEncoding.SRGB,
                WorldRenderFrame.CompositeStage.FINAL_COLOR,
                material));
    }

    @Override
    public boolean isAuxiliaryPass() {
        return RenderCompat.isRenderingShadowPass();
    }
}
