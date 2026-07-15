package fr.lacaleche.glue.client.render.internal.material;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jetbrains.annotations.Nullable;

interface TerrainMaterialCapture {

    void beginFrame(long frameSequence);

    void cancelFrame();

    /** GL texture id of the captured material color for the current frame, or -1 if none. */
    int colorTextureId();

    /** GL depth texture id matching {@link #colorTextureId()}, or -1 if none. */
    int depthTextureId();

    /**
     * The framebuffer holding the current frame's material (colour + depth), or null when no
     * coherent capture exists. Dynamic geometry (entities, particles) is drawn into this same
     * target after terrain so Lumos reads them all through one material path.
     */
    @Nullable
    RenderTarget renderTarget();

    void cleanup();
}
