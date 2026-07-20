package fr.lacaleche.glue.mcsx.surface;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Supplies the texture an {@link ExternalSurfaceView} blits each frame. Implementations
 * render their content to an offscreen target sized to the view and return its colour
 * texture view. Invoked on the render thread once per frame, immediately before the blit.
 */
@FunctionalInterface
public interface SurfaceSource {

    /**
     * Render this frame's content at the given pixel size and return the texture to blit,
     * or {@code null} to skip the blit this frame (e.g. before the target exists).
     *
     * @param widthPx   the view's current width in framebuffer pixels
     * @param heightPx  the view's current height in framebuffer pixels
     * @param deltaTick the Minecraft partial-tick delta for this frame
     */
    GpuTextureView render(int widthPx, int heightPx, float deltaTick);
}
