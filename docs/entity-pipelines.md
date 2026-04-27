# Entity Shader Pipelines (GluePipeline)

`GluePipeline` is a high-level API for applying custom shaders to block entity rendering. It creates a `RenderPipeline`, wraps it into a `ShadedBufferSource`, and handles Iris compatibility automatically.

## Creating a Pipeline

### Using Factory Methods

```java
GluePipeline pipeline = GluePipeline.entity(
        MyMod.id("my_shader"),                          // Pipeline name
        MyMod.id("core/my_shader"),                     // Vertex shader
        MyMod.id("core/my_shader")                      // Fragment shader
);
```

This creates a pipeline with:
- `NEW_ENTITY` vertex format (position, color, UV, overlay, light, normal)
- `TRANSLUCENT` blend function
- Iris program `ENTITIES_TRANSLUCENT`
- Samplers: `Sampler0`, `Sampler1`, `Sampler2`
- Alpha cutout at 0.1

### Using the Builder (Full Control)

For cases where the factory methods don't offer enough control, use the builder:

```java
GluePipeline pipeline = GluePipeline.builder(
        MyMod.id("custom_pipeline"),
        MyMod.id("core/custom"),
        MyMod.id("core/custom"))
    .snippet(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
    .vertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
    .blend(BlendFunction.ADDITIVE)
    .cull(true)
    .alphaCutout(0.5f)
    .samplers("Sampler0", "CustomSampler")
    .irisProgram("PARTICLES")
    .category(GluePipeline.PipelineCategory.PARTICLE)
    .build();
```

Builder defaults match the `entity()` factory method. Call only the methods you need to override.

## Factory Methods

| Method | Vertex Format | Default Iris Program | Blend |
|---|---|---|---|
| `entity(loc, vert, frag)` | `NEW_ENTITY` | `ENTITIES_TRANSLUCENT` | Translucent |
| `entity(loc, vert, frag, blend)` | `NEW_ENTITY` | `ENTITIES_TRANSLUCENT` | Custom |
| `entityCustom(loc, vert, frag, blend, iris)` | `NEW_ENTITY` | Custom | Custom |
| `block(loc, vert, frag)` | `BLOCK` | `TERRAIN` | Translucent |
| `block(loc, vert, frag, blend, iris)` | `BLOCK` | Custom | Custom |
| `particle(loc, vert, frag)` | `PARTICLE` | `PARTICLES` | Translucent |
| `particle(loc, vert, frag, blend, iris)` | `PARTICLE` | Custom | Custom |

## Pipeline Categories

Each pipeline has a `PipelineCategory` (`ENTITY`, `BLOCK`, or `PARTICLE`) that determines:
- Which `RenderType` factory is used when calling `renderType(texture)`
- Whether overlay state is included (entity only)

When using `ShadedBufferSource`, the category is used automatically to create the correct `RenderType` for each draw call.

## Using in a Block Entity Renderer

```java
public class MyBlockEntityRenderer implements BlockEntityRenderer<MyBlockEntity> {

    private final GluePipeline pipeline;

    public MyBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.pipeline = GluePipeline.entity(
                MyMod.id("my_shader"),
                MyMod.id("core/my_shader"),
                MyMod.id("core/my_shader"));
    }

    @Override
    public void render(MyBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay, Vec3 cameraPos) {
        if (RenderCompat.isRenderingShadowPass()) return;

        ShadedBufferSource shadedSource = pipeline.wrap();

        // Render items/models through the shaded source — textures are
        // automatically routed through the custom shader pipeline
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                light, overlay, poseStack, shadedSource, entity.getLevel(), 0);

        shadedSource.endBatch();
    }
}
```

## How It Works

1. `pipeline.wrap()` creates a `ShadedBufferSource` that intercepts all `getBuffer()` calls
2. For each `RenderType` submitted, the texture is extracted via mixin accessors
3. A new `RenderType` is created using the pipeline's category (entity/block/particle) and the extracted texture
4. When `endBatch()` is called:
   - **Vanilla mode:** draws immediately
   - **Iris mode:** renders into a private capture FBO with `ImmediateState.bypass = true`, then blits back to the main framebuffer after Iris compositing

## Capture & Blit Pipeline (Iris)

When Iris is active, the rendering flow is:

```
endBatch() → capture FBO → [Iris compositing happens] → postCompositeBlit() → main FBO
```

The `GlueDrawBufferFixMixin` redirects draw calls to the capture FBO during the bypass window. The `GluePostCompositeMixin` triggers the blit back to the main framebuffer after Iris compositing, preserving correct depth testing.

## Shader Files

Place shaders at:

```
assets/<modid>/shaders/core/<name>.vsh
assets/<modid>/shaders/core/<name>.fsh
```

Entity shaders receive the standard MC uniforms (`ModelViewMat`, `ProjMat`, `FogStart`, `FogEnd`, `FogColor`, `Light0_Direction`, `Light1_Direction`) plus samplers `Sampler0` (texture), `Sampler1` (overlay), `Sampler2` (light map).

## RenderType Access

```java
// Preferred: automatically dispatches based on pipeline category
RenderType type = pipeline.renderType(textureLocation);

// Direct access (for when you know the pipeline category):
RenderType entityType   = pipeline.entityType(textureLocation);
RenderType blockType    = pipeline.blockType(textureLocation);
RenderType particleType = pipeline.particleType(textureLocation);
```

Render types are cached per texture location.
