package fr.lacaleche.jcef;

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Uses only Blaze3D state-managed APIs. BGRA and premultiplied alpha stay intact until sampling. */
final class SurfaceRenderer implements AutoCloseable {

    private static final RenderPipeline BGRA = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("jcef-experiment", "pipeline/bgra"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("jcef-experiment", "core/web"))
            .withShaderDefine("SOURCE_BGRA")
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).build());
    private static final RenderPipeline RGBA = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("jcef-experiment", "pipeline/rgba"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("jcef-experiment", "core/web"))
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).build());

    private GpuTexture texture;
    private GpuTextureView view;
    private ByteBuffer conversion;
    private int width;
    private int height;

    static void registerPipelines() {
        // Initializes the two static pipelines before Minecraft's shader resource reload.
    }

    Upload upload(FrameMailbox.Transfer frame, CefSurface.UploadMode mode) {
        if (this.texture == null || this.width != frame.width() || this.height != frame.height()) {
            this.close();
            this.width = frame.width();
            this.height = frame.height();
            this.texture = RenderSystem.getDevice().createTexture("JCEF web surface",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                    this.width, this.height, 1, 1);
            this.view = RenderSystem.getDevice().createTextureView(this.texture);
        }
        IntBuffer pixels = frame.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        long convertStarted = System.nanoTime();
        if (mode == CefSurface.UploadMode.CPU_RGBA) {
            if (this.conversion == null || this.conversion.capacity() < frame.region().bytes()) {
                this.conversion = ByteBuffer.allocateDirect(frame.region().bytes()).order(ByteOrder.LITTLE_ENDIAN);
            }
            IntBuffer target = this.conversion.clear().limit(frame.region().bytes()).asIntBuffer();
            for (int i = 0; i < pixels.remaining(); i++) target.put(i, swizzle(pixels.get(i)));
            pixels = target;
        }
        long uploadStarted = System.nanoTime();
        FrameMailbox.Region region = frame.region();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels, NativeImage.Format.RGBA,
                0, 0, region.x(), region.y(), region.width(), region.height());
        return new Upload(mode == CefSurface.UploadMode.CPU_RGBA ? uploadStarted - convertStarted : 0,
                System.nanoTime() - uploadStarted);
    }

    void draw(GuiGraphics graphics, int x, int y, int width, int height, CefSurface.UploadMode mode) {
        if (this.view == null || width <= 0 || height <= 0) return;
        graphics.guiRenderState.submitGuiElement(new BlitRenderState(mode == CefSurface.UploadMode.GPU_BGRA ? BGRA : RGBA,
                TextureSetup.singleTexture(this.view), new Matrix3x2f(graphics.pose()), x, y, x + width, y + height,
                0, 1, 0, 1, -1, graphics.scissorStack.peek()));
    }

    @Override
    public void close() {
        if (this.view != null) this.view.close();
        if (this.texture != null) this.texture.close();
        this.view = null;
        this.texture = null;
        this.conversion = null;
    }

    static int swizzle(int bgra) {
        return (bgra & 0xFF00FF00) | (bgra >>> 16 & 0xFF) | (bgra & 0xFF) << 16;
    }

    record Upload(long conversionNanos, long uploadNanos) {
    }
}
