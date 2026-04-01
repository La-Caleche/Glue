# Iris / Oculus Compatibility

Glue handles Iris/Oculus shader pack compatibility at three levels:

## 1. Pipeline Registration

When registering core shader pipelines, pass an Iris program name so shader packs apply correct rendering:

```java
CORE.register("my_shader", snippet, builder -> { ... }, "BLOCK_TRANSLUCENT");
```

Internally calls `IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.BLOCK_TRANSLUCENT)`.

## 2. Shadow Pass Detection

During Iris shadow passes, block entities are re-rendered from the light's perspective. Custom rendering should be skipped to avoid ghost duplicates:

```java
if (RenderCompat.isRenderingShadowPass()) return;
```

## 3. Post-Processing Shader Compatibility

`PostShaderHandle.apply()` handles two Iris issues automatically:

### FBO Blit

When Iris is active, it redirects the main render target to its own composite FBO. PostChain reads from `target.getColorTexture()`, which doesn't contain the current scene. Glue blits the scene:

1. **Before processing:** Blit from Iris's FBO → MC's main render target
2. **Process:** Run PostChain with `withIrisBypass()`
3. **After processing:** Blit post-processed result back to Iris's FBO

### Pipeline Override Bypass

Iris's `redirectIrisProgram` mixin intercepts pipeline compilation. `withIrisBypass()` sets `ImmediateState.bypass = true` so the post shader compiles with vanilla GLSL.

### GL State Preservation

`apply()` saves and restores:
- FBO binding
- Viewport
- Depth test, blend, scissor test states

## 4. Deferred Draw Queue

Raw GL draws (via `ShaderRenderer`) are dispatched through `DeferredDrawQueue`:

- **Iris active:** Draws are deferred to `WorldRenderEvents.LAST` (after all world compositing)
- **Iris shadow pass:** Draws are silently dropped
- **Vanilla:** Draws execute immediately

## API Reference

| Method | Description |
|---|---|
| `RenderCompat.HAS_IRIS` | Whether Iris/Oculus is loaded |
| `RenderCompat.isIrisShaderEnabled()` | Whether a shader pack is active |
| `RenderCompat.isRenderingShadowPass()` | Whether in a shadow pass |
| `RenderCompat.assignIrisProgram(pipeline, name)` | Register pipeline with Iris |
| `RenderCompat.withIrisBypass(action)` | Run action bypassing Iris pipeline hooks |

## Implementation Details

- `RenderCompat` — public API, safe to call without Iris
- `IrisProxy` — package-private, isolated Iris class loading (only loaded when Iris is present)
- `FramebufferHelper.getFramebufferId()` — gets GL FBO ID via `GlTexture.getFbo()`, same mechanism as Iris's `iris$bindFramebuffer()`
