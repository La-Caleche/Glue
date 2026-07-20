package fr.lacaleche.glue.mcsx.surface;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.textures.GpuTextureView;
import fr.lacaleche.glue.mcsx.mui.MuiModApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render-thread compositor that blits each registered {@link ExternalSurfaceView}'s texture
 * into its current window rect, layered below the Modern UI (the view punches a transparent hole
 * so the texture shows through). Views register while attached and publish their bounds from the
 * UI thread; the host reads those bounds and draws on the render thread from the Modern UI host's
 * render path, before the UI layer is composited on top.
 *
 * <p>The texture's V axis is flipped relative to the GUI: offscreen render targets are
 * lower-left origin while the GUI is upper-left.
 */
public final class ExternalSurfaceHost {

    private static final ExternalSurfaceHost INSTANCE = new ExternalSurfaceHost();

    public static ExternalSurfaceHost getInstance() {
        return INSTANCE;
    }

    private record Bounds(int x, int y, int w, int h) {
    }

    private final Map<ExternalSurfaceView, Bounds> surfaces = new ConcurrentHashMap<>();

    private ExternalSurfaceHost() {
    }

    void register(ExternalSurfaceView view) {
        surfaces.putIfAbsent(view, new Bounds(0, 0, 0, 0));
    }

    void unregister(ExternalSurfaceView view) {
        surfaces.remove(view);
    }

    void updateBounds(ExternalSurfaceView view, int x, int y, int w, int h) {
        if (surfaces.containsKey(view)) {
            surfaces.put(view, new Bounds(x, y, w, h));
        }
    }

    /** One rendered surface and its window rect, for the embedding present path. */
    public record SurfaceDraw(GpuTextureView texture, int x, int y, int w, int h) {
    }

    /**
     * Renders every surface source and returns the textures with their window rects, for a
     * caller that composites them itself (the dockspace's full-resolution present) instead of
     * going through the pane-sized GUI pipeline. Render thread, once per frame.
     */
    public java.util.List<SurfaceDraw> renderDirect(float deltaTick) {
        if (surfaces.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<SurfaceDraw> draws = new java.util.ArrayList<>();
        for (Map.Entry<ExternalSurfaceView, Bounds> entry : surfaces.entrySet()) {
            Bounds b = entry.getValue();
            if (b.w() <= 0 || b.h() <= 0) {
                continue;
            }
            GpuTextureView texture = entry.getKey().source().render(b.w(), b.h(), deltaTick);
            if (texture != null) {
                draws.add(new SurfaceDraw(texture, b.x(), b.y(), b.w(), b.h()));
            }
        }
        return draws;
    }

    /**
     * Blit every registered surface below the UI. Called once per frame on the render thread from
     * the Modern UI host, before the UI layer is composited over it.
     */
    public void composite(GuiGraphics gr, Window window, float deltaTick) {
        if (surfaces.isEmpty()) {
            return;
        }
        for (Map.Entry<ExternalSurfaceView, Bounds> entry : surfaces.entrySet()) {
            Bounds b = entry.getValue();
            if (b.w() <= 0 || b.h() <= 0) {
                continue;
            }
            GpuTextureView texture = entry.getKey().source().render(b.w(), b.h(), deltaTick);
            if (texture == null) {
                continue;
            }
            gr.nextStratum();
            MuiModApi.get().submitGuiElementRenderState(gr, new BlitRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.singleTexture(texture),
                    new Matrix3x2f().scale(1.0F / window.getGuiScale()),
                    b.x(), b.y(), b.x() + b.w(), b.y() + b.h(),
                    0.0F, 1.0F, 1.0F, 0.0F,
                    ~0,
                    null
            ));
        }
    }
}
