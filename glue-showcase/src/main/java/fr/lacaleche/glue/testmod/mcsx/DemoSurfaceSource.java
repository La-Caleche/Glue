package fr.lacaleche.glue.testmod.mcsx;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import fr.lacaleche.glue.mcsx.surface.SurfaceGestureListener;
import fr.lacaleche.glue.mcsx.surface.SurfaceSource;
import net.minecraft.util.ARGB;

/**
 * The smallest thing that proves an MCSX-laid-out screen can host a live surface: it clears an
 * offscreen target to a colour that cycles over time, so a static screenshot cannot fake it, and
 * shifts that colour on drag / scroll so the routed gestures are visible too. A real consumer
 * (the Ignis VFX editor) renders its particle scene here instead; a spinning 3D shape would add a
 * {@code RenderPass} + pipeline on top of this same skeleton.
 *
 * <p>Every GPU call runs inside {@link #render}, which the host invokes on the render thread — the
 * constructor allocates nothing, so this is safe to build headlessly (e.g. under the lint test).
 */
public final class DemoSurfaceSource implements SurfaceSource, SurfaceGestureListener {

    private static final long CYCLE_NANOS = 6_000_000_000L;

    private TextureTarget target;
    private int currentWidth;
    private int currentHeight;

    private volatile float hue;
    private volatile float brightness = 1.0f;
    private float lastDragX;

    @Override
    public GpuTextureView render(int widthPx, int heightPx, float deltaTick) {
        if (widthPx <= 0 || heightPx <= 0) {
            return null;
        }
        if (target == null) {
            target = new TextureTarget("mcsx-demo-surface", widthPx, heightPx, false);
        } else if (currentWidth != widthPx || currentHeight != heightPx) {
            target.resize(widthPx, heightPx);
        }
        currentWidth = widthPx;
        currentHeight = heightPx;

        float phase = (System.nanoTime() % CYCLE_NANOS) / (float) CYCLE_NANOS * (float) (Math.PI * 2.0);
        phase += hue;
        float r = (0.5f + 0.5f * (float) Math.sin(phase)) * brightness;
        float g = (0.5f + 0.5f * (float) Math.sin(phase + 2.094f)) * brightness;
        float b = (0.5f + 0.5f * (float) Math.sin(phase + 4.189f)) * brightness;

        RenderSystem.getDevice().createCommandEncoder()
                .clearColorTexture(target.getColorTexture(), ARGB.colorFromFloat(1.0f, r, g, b));
        return target.getColorTextureView();
    }

    @Override
    public boolean onSurfaceDown(float x, float y, int button) {
        lastDragX = x;
        return true;
    }

    @Override
    public void onSurfaceMove(float x, float y) {
        hue += (x - lastDragX) * 0.01f;
        lastDragX = x;
    }

    @Override
    public boolean onSurfaceScroll(float amount, float x, float y) {
        brightness = Math.clamp(brightness + amount * 0.05f, 0.2f, 1.0f);
        return true;
    }
}
