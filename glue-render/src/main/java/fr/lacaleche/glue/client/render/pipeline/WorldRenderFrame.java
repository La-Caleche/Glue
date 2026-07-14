package fr.lacaleche.glue.client.render.pipeline;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Optional;

/**
 * A coherent view of one rendered world frame.
 *
 * <p>All targets, matrices, dimensions, and optional material data must describe the
 * same render stage. Consumers must not replace individual fields with values queried
 * from a different renderer.</p>
 */
@Environment(EnvType.CLIENT)
public record WorldRenderFrame(
        long sequence,
        int sourceFramebufferId,
        int destinationFramebufferId,
        int sceneColorTextureId,
        int sceneDepthTextureId,
        int width,
        int height,
        Matrix4fc viewMatrix,
        Matrix4fc projectionMatrix,
        Vector3dc cameraPosition,
        ColorEncoding colorEncoding,
        CompositeStage compositeStage,
        Optional<MaterialFrame> material
) {
    public WorldRenderFrame {
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (sourceFramebufferId <= 0 || destinationFramebufferId <= 0
                || sceneColorTextureId <= 0 || sceneDepthTextureId <= 0) {
            throw new IllegalArgumentException("frame targets and textures must be valid OpenGL names");
        }
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("frame dimensions must be positive");
        viewMatrix = new Matrix4f(viewMatrix);
        projectionMatrix = new Matrix4f(projectionMatrix);
        cameraPosition = new Vector3d(cameraPosition);
        if (colorEncoding == null) throw new NullPointerException("colorEncoding");
        if (compositeStage == null) throw new NullPointerException("compositeStage");
        if (material == null) throw new NullPointerException("material");
        material.ifPresent(frame -> {
            if (frame.frameSequence() != sequence) {
                throw new IllegalArgumentException("material belongs to another frame");
            }
            if (frame.width() != width || frame.height() != height) {
                throw new IllegalArgumentException("material dimensions do not match the scene");
            }
        });
    }

    public Matrix4f viewProjection() {
        return new Matrix4f(projectionMatrix).mul(viewMatrix);
    }

    public enum ColorEncoding {
        SRGB,
        LINEAR
    }

    public enum CompositeStage {
        FINAL_COLOR,
        PRE_TONEMAP
    }
}
