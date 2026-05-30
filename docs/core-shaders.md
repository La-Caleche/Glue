# Core Shaders & Render Pipelines

`CoreShaderRegistry` registers `RenderPipeline` objects into vanilla's `RenderPipelines`. There are two registration methods:

- **`registerRaw()`** — registers a vanilla `RenderPipeline`. Not tracked in `GlueClientRegistries.PIPELINES`. Use for GUI shaders, simple draw calls.
- **`registerPipeline()`** — registers a `GluePipeline` (Glue's wrapper with capture/blit for Iris). Tracked in `GlueClientRegistries.PIPELINES`, can be looked up by ID, shows up alongside JSON pipelines.

## Setup

```java
public static final CoreShaderRegistry CORE = new CoreShaderRegistry("mymod", MyMod::id);
```

## registerRaw — Vanilla Pipeline

```java
public static final RenderPipeline MY_GUI_SHADER = CORE.registerRaw("my_shader",
        RenderPipelines.MATRICES_PROJECTION_SNIPPET,
        builder -> builder
                .withVertexShader(MyMod.id("core/my_shader"))
                .withFragmentShader(MyMod.id("core/my_shader"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
);
```

With Iris program assignment:

```java
public static final RenderPipeline WORLD_SHADER = CORE.registerRaw("my_world_shader",
        RenderPipelines.MATRICES_PROJECTION_SNIPPET,
        builder -> builder
                .withVertexShader(MyMod.id("core/my_world_shader"))
                .withFragmentShader(MyMod.id("core/my_world_shader"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS),
        "BLOCK_TRANSLUCENT"
);
```

## registerPipeline — GluePipeline (tracked in registry)

```java
public static final GluePipeline MY_ENTITY_SHADER = CORE.registerPipeline("my_entity",
        MyMod.id("core/entity"),
        MyMod.id("core/my_effect"),
        builder -> builder
                .blend(BlendFunction.TRANSLUCENT)
                .irisProgram("ENTITIES_TRANSLUCENT")
);
```

This pipeline is accessible via `GlueClientRegistries.PIPELINES.get(MyMod.id("my_entity"))`.

## Data-Driven Pipelines (JSON)

Instead of Java, you can define pipelines in `assets/<modid>/glue/pipelines/<name>.json`:

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

These are loaded by `PipelineDefinitionLoader` and pushed into `GlueClientRegistries.PIPELINES`. Hot-reloadable via F3+T.

## Shader Files

```
assets/<modid>/shaders/core/<name>.vsh
assets/<modid>/shaders/core/<name>.fsh
```

## Using in GUI

```java
guiGraphics.fill(MY_GUI_PIPELINE, x, y, x + w, y + h, color);
```
