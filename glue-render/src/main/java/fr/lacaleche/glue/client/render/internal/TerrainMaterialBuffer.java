package fr.lacaleche.glue.client.render.internal;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Captures linear terrain albedo by replaying the already-visible opaque chunk VBOs. */
public final class TerrainMaterialBuffer {

    private static final boolean HAS_SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
    private static RenderPipeline solidPipeline;
    private static RenderPipeline cutoutMippedPipeline;
    private static RenderPipeline cutoutPipeline;
    private static RenderTarget target;
    private static boolean requested;
    private static boolean available;

    private TerrainMaterialBuffer() {
    }

    public static void init() {
        ResourceLocation vertex = ResourceLocation.fromNamespaceAndPath(
                "glue", "core/terrain_material");
        ResourceLocation fragment = ResourceLocation.fromNamespaceAndPath(
                "glue", "core/terrain_material");
        RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withVertexShader(vertex)
                .withFragmentShader(fragment)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .buildSnippet();

        solidPipeline = register("terrain_material_solid", snippet, null);
        cutoutMippedPipeline = register("terrain_material_cutout_mipped", snippet, 0.5f);
        cutoutPipeline = register("terrain_material_cutout", snippet, 0.1f);
    }

    private static RenderPipeline register(String name, RenderPipeline.Snippet snippet, Float alphaCutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet)
                .withLocation(ResourceLocation.fromNamespaceAndPath("glue", "pipeline/" + name));
        if (alphaCutout != null) builder.withShaderDefine("ALPHA_CUTOUT", alphaCutout);
        return RenderPipelines.register(builder.build());
    }

    /** Requests one capture during this frame's opaque terrain draw. */
    public static void beginFrame(boolean needed) {
        requested = needed;
        available = false;
    }

    public static void capture(ChunkSectionsToRender sections) {
        if (!requested) return;
        requested = false;
        if (HAS_SODIUM || RenderCompat.isIrisShaderEnabled() || RenderCompat.isRenderingShadowPass()
                || sections.drawsPerLayer() == null) return;

        RenderCompat.withIrisFullBypass(() -> captureVanilla(sections));
    }

    private static void captureVanilla(ChunkSectionsToRender sections) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || main.getDepthTextureView() == null) return;

        if (target == null) {
            target = new TextureTarget("Glue terrain material", main.width, main.height, true);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height);
        }
        target.copyDepthFrom(main);

        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = sections.maxIndicesRequired() == 0
                ? null : sequential.getBuffer(sections.maxIndicesRequired());
        VertexFormat.IndexType indexType = sections.maxIndicesRequired() == 0 ? null : sequential.type();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Glue terrain material",
                target.getColorTextureView(), OptionalInt.of(0),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            drawLayer(pass, sections, ChunkSectionLayer.SOLID, solidPipeline, indexBuffer, indexType);
            drawLayer(pass, sections, ChunkSectionLayer.CUTOUT_MIPPED,
                    cutoutMippedPipeline, indexBuffer, indexType);
            drawLayer(pass, sections, ChunkSectionLayer.CUTOUT, cutoutPipeline, indexBuffer, indexType);
        }
        available = true;
    }

    private static void drawLayer(RenderPass pass, ChunkSectionsToRender sections,
                                  ChunkSectionLayer layer, RenderPipeline pipeline,
                                  GpuBuffer indexBuffer, VertexFormat.IndexType indexType) {
        List<RenderPass.Draw<GpuBufferSlice[]>> draws = sections.drawsPerLayer().get(layer);
        if (draws == null || draws.isEmpty()) return;
        pass.setPipeline(pipeline);
        pass.bindSampler("Sampler0", layer.textureView());
        pass.drawMultipleIndexed(draws, indexBuffer, indexType,
                List.of("DynamicTransforms"), sections.dynamicTransforms());
    }

    public static boolean isAvailable() {
        return available && target != null;
    }

    public static int getColorTextureId() {
        return isAvailable() ? FramebufferHelper.getColorTextureId(target) : -1;
    }

    public static int getDepthTextureId() {
        return isAvailable() ? FramebufferHelper.getDepthTextureId(target) : -1;
    }

    public static void cleanup() {
        available = false;
        requested = false;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
