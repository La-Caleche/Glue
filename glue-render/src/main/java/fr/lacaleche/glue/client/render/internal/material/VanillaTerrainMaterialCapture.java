package fr.lacaleche.glue.client.render.internal.material;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

final class VanillaTerrainMaterialCapture implements TerrainMaterialCapture {

    private final MaterialCaptureTarget target = new MaterialCaptureTarget("Glue vanilla terrain material");
    private RenderPipeline solidPipeline;
    private RenderPipeline cutoutMippedPipeline;
    private RenderPipeline cutoutPipeline;
    private boolean requested;
    private boolean available;

    void init() {
        ResourceLocation shader = ResourceLocation.fromNamespaceAndPath(
                "glue", "internal/material/terrain_material");
        RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .buildSnippet();

        solidPipeline = register("terrain_material_solid", snippet, null);
        cutoutMippedPipeline = register("terrain_material_cutout_mipped", snippet, 0.5f);
        cutoutPipeline = register("terrain_material_cutout", snippet, 0.1f);
    }

    @Override
    public void beginFrame(long sequence) {
        requested = true;
        available = false;
    }

    @Override
    public void cancelFrame() {
        requested = false;
        available = false;
    }

    void capture(ChunkSectionsToRender sections) {
        if (!requested) return;
        requested = false;
        if (RenderCompat.isRenderingShadowPass() || sections.drawsPerLayer() == null) return;
        RenderCompat.withIrisFullBypass(() -> render(sections));
    }

    private void render(ChunkSectionsToRender sections) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getDepthTextureView() == null) return;

        target.prepare(main);
        target.copyDepthFrom(main);

        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = sections.maxIndicesRequired() == 0
                ? null : sequential.getBuffer(sections.maxIndicesRequired());
        VertexFormat.IndexType indexType = sections.maxIndicesRequired() == 0 ? null : sequential.type();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Glue terrain material",
                target.renderTarget().getColorTextureView(), OptionalInt.of(0),
                target.renderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            drawLayer(pass, sections, ChunkSectionLayer.SOLID, solidPipeline, indexBuffer, indexType);
            drawLayer(pass, sections, ChunkSectionLayer.CUTOUT_MIPPED,
                    cutoutMippedPipeline, indexBuffer, indexType);
            drawLayer(pass, sections, ChunkSectionLayer.CUTOUT, cutoutPipeline, indexBuffer, indexType);
        }
        available = true;
    }

    @Override
    public int colorTextureId() {
        return available ? target.colorTextureId() : -1;
    }

    @Override
    public int depthTextureId() {
        return available ? target.depthTextureId() : -1;
    }

    @Override
    public void cleanup() {
        cancelFrame();
        target.cleanup();
    }

    private static RenderPipeline register(String name, RenderPipeline.Snippet snippet, Float alphaCutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet)
                .withLocation(ResourceLocation.fromNamespaceAndPath("glue", "pipeline/" + name));
        if (alphaCutout != null) builder.withShaderDefine("ALPHA_CUTOUT", alphaCutout);
        return RenderPipelines.register(builder.build());
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
}
