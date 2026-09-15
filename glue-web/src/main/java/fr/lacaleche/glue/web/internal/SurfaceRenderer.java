package fr.lacaleche.glue.web.internal;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Blaze3D-owned textures; channel conversion and premultiplied blending happen in the shader. */
final class SurfaceRenderer implements AutoCloseable {

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("glue-web", "pipeline/surface"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("glue-web", "core/web"))
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).build();

    private GpuTexture texture;
    private GpuTextureView view;
    private int width;
    private int height;

    static void registerPipeline() {
        RenderPipelines.register(PIPELINE);
    }

    long upload(FrameMailbox.Transfer frame) {
        if (this.texture == null || this.width != frame.width() || this.height != frame.height()) {
            this.close();
            this.width = frame.width();
            this.height = frame.height();
            this.texture = RenderSystem.getDevice().createTexture("Glue web surface",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                    this.width, this.height, 1, 1);
            this.view = RenderSystem.getDevice().createTextureView(this.texture);
        }
        IntBuffer pixels = frame.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        FrameMailbox.Region region = frame.region();
        long started = System.nanoTime();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels, NativeImage.Format.RGBA,
                0, 0, region.x(), region.y(), region.width(), region.height());
        return System.nanoTime() - started;
    }

    void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (this.view == null || width <= 0 || height <= 0) return;
        graphics.guiRenderState.submitGuiElement(new BlitRenderState(PIPELINE,
                TextureSetup.singleTexture(this.view), new Matrix3x2f(graphics.pose()), x, y, x + width, y + height,
                0, 1, 0, 1, -1, graphics.scissorStack.peek()));
    }

    @Override
    public void close() {
        if (this.view != null) this.view.close();
        if (this.texture != null) this.texture.close();
        this.view = null;
        this.texture = null;
    }
}
