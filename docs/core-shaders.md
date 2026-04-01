# Core Shaders & Render Pipelines

In MC 1.21.8, core shaders are `RenderPipeline` objects (not the old `ShaderInstance`). Glue's `CoreShaderRegistry` wraps the builder pattern.

## Registration

```java
public static final CoreShaderRegistry CORE = new CoreShaderRegistry("mymod", MyMod::id);

public static final RenderPipeline MY_PIPELINE = CORE.register("my_shader",
        RenderPipelines.MATRICES_PROJECTION_SNIPPET,    // Base snippet
        builder -> builder
                .withVertexShader(MyMod.id("core/my_shader"))
                .withFragmentShader(MyMod.id("core/my_shader"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
);
```

The pipeline is registered with `RenderPipelines.register()` and gets a location of `mymod:pipeline/my_shader`.

## With Iris Compatibility

Pass an Iris program name as the fourth argument:

```java
public static final RenderPipeline WORLD_SHADER = CORE.register("my_world_shader",
        RenderPipelines.MATRICES_PROJECTION_SNIPPET,
        builder -> builder
                .withVertexShader(MyMod.id("core/my_world_shader"))
                .withFragmentShader(MyMod.id("core/my_world_shader"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS),
        "BLOCK_TRANSLUCENT"  // Iris program category
);
```

Valid Iris programs: `BASIC`, `TEXTURED`, `TEXTURED_LIT`, `SKYBOX`, `CLOUDS`, `BLOCK_TRANSLUCENT`, etc.

## Shader Files

Place shaders at:
```
assets/<modid>/shaders/core/<name>.vsh
assets/<modid>/shaders/core/<name>.fsh
```

## Creating RenderTypes

```java
RenderType.CompositeRenderType myType = CoreShaderRegistry.createRenderType(
        "my_render_type",
        MY_PIPELINE,
        RenderType.CompositeState.builder().createCompositeState(false));
```

## Using in GUI

MC 1.21.8's `GuiGraphics.fill()` accepts a `RenderPipeline`:

```java
guiGraphics.fill(MY_GUI_PIPELINE, x, y, x + w, y + h, color);
```
