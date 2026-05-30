# Entity Shader Pipelines (GluePipeline)

`GluePipeline` is a high-level API for applying custom shaders to entity/block/particle rendering. It creates a `RenderPipeline`, wraps it into a `ShadedBufferSource`, and handles Iris compatibility automatically.

## Registration

### Java — Factory Methods

```java
GluePipeline pipeline = GluePipeline.entity(
        MyMod.id("my_shader"),
        MyMod.id("core/my_shader"),
        MyMod.id("core/my_shader")
);
```

### Java — Builder (full control)

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

### Java — via CoreShaderRegistry (tracked in registry)

```java
public static final GluePipeline MY_PIPELINE = CORE.registerPipeline("my_shader",
        MyMod.id("core/entity"),
        MyMod.id("core/my_effect"),
        builder -> builder
                .blend(BlendFunction.TRANSLUCENT)
                .irisProgram("ENTITIES_TRANSLUCENT")
);
```

This is stored in `GlueClientRegistries.PIPELINES` and can be looked up by ID.

### Data-Driven (JSON)

Create `assets/<modid>/glue/pipelines/<name>.json`:

```json
{
    "vertex_shader": "mymod:core/entity",
    "fragment_shader": "mymod:core/my_effect",
    "blend": "translucent",
    "alpha_cutout": 0.1,
    "cull": false,
    "iris_program": "ENTITIES_TRANSLUCENT",
    "category": "entity"
}
```

Hot-reloadable via F3+T. Stored in `GlueClientRegistries.PIPELINES`.

## Factory Methods

| Method | Vertex Format | Iris Program | Blend |
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

## Usage in a Block Entity Renderer

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

        itemRenderer.renderStatic(stack, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                light, overlay, poseStack, shadedSource, entity.getLevel(), 0);

        shadedSource.endBatch();
    }
}
```

## How It Works

1. `pipeline.wrap()` creates a `ShadedBufferSource` that intercepts `getBuffer()` calls
2. For each `RenderType`, the texture is extracted and a new `RenderType` is created using the pipeline's shader
3. On `endBatch()`:
   - **Vanilla:** draws immediately
   - **Iris:** renders into a capture FBO with `ImmediateState.bypass = true`, then blits back after Iris compositing

## RenderType Access

```java
RenderType type = pipeline.renderType(textureLocation);  // auto-dispatches by category

// Direct access:
RenderType entityType   = pipeline.entityType(textureLocation);
RenderType blockType    = pipeline.blockType(textureLocation);
RenderType particleType = pipeline.particleType(textureLocation);
```

Render types are cached per texture location.

## Shader Files

```
assets/<modid>/shaders/core/<name>.vsh
assets/<modid>/shaders/core/<name>.fsh
```

Entity shaders receive standard MC uniforms (`ModelViewMat`, `ProjMat`, `FogStart`, `FogEnd`, `FogColor`, `Light0_Direction`, `Light1_Direction`) plus samplers `Sampler0` (texture), `Sampler1` (overlay), `Sampler2` (light map).
