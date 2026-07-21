package fr.lacaleche.glue.client.render.light.internal.pipeline;

import org.joml.Matrix4f;

/** Per-frame render targets and matrices Lumos reads from Minecraft's main render target. */
record LumosFrame(
        int framebufferId,
        int sceneDepthTextureId,
        int width,
        int height,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix
) {
}
