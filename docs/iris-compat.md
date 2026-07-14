# Iris / Oculus Compatibility

Glue handles Iris/Oculus shader pack compatibility at multiple levels.

## 1. Pipeline Registration

When registering core shader pipelines, pass an Iris program name so shader packs apply correct rendering:

```java
CORE.registerRaw("my_shader", snippet, builder -> { ... }, "BLOCK_TRANSLUCENT");
```

Internally calls `IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.BLOCK_TRANSLUCENT)`.

For `GluePipeline`, Iris registration is automatic — see [Entity Pipelines](entity-pipelines.md).

## 2. Shadow Pass Detection

During Iris shadow passes, block entities are re-rendered from the light's perspective. Custom rendering should be skipped to avoid ghost duplicates:

```java
if (RenderCompat.isRenderingShadowPass()) return;
```

## 3. Entity Shader Capture Pipeline

When Iris is active, `ShadedBufferSource` (used by `GluePipeline`) cannot draw directly because Iris intercepts all pipeline compilations. Instead:

1. **Capture:** Draws are rendered into a private FBO with `ImmediateState.bypass = true`
2. **Depth copy:** Scene depth is blitted from Iris's FBO to the capture FBO once per frame
3. **Blit:** After Iris compositing, `GluePostCompositeMixin` triggers `postCompositeBlit()` which depth-tests the capture against the scene and composites onto the main framebuffer

This is handled automatically by `GluePipeline.wrap()` + `ShadedBufferSource.endBatch()`.

## 4. Post-Processing Shader Compatibility

`PostShaderHandle.apply()` handles two Iris issues automatically:

### FBO Blit

When Iris is active, it redirects the main render target to its own composite FBO. PostChain reads from `target.getColorTexture()`, which doesn't contain the current scene. Glue blits the scene:

1. **Before processing:** Blit from Iris's FBO → MC's main render target
2. **Process:** Run PostChain with `withIrisBypass()`
3. **After processing:** Blit post-processed result back to Iris's FBO

### Pipeline Override Bypass

Iris's `redirectIrisProgram` mixin intercepts pipeline compilation. `withIrisBypass()` sets `ImmediateState.bypass = true` so the post shader compiles with vanilla GLSL.

### GL State Preservation

`apply()` uses the shared `SavedGlState` utility to save and restore comprehensive GL state:
program, read/draw FBOs, the complete MRT draw-buffer vector and read buffer, bound array buffer,
VAO, texture bindings for units 0–8, blend (with per-channel src/dst), depth test/func/mask,
cull, scissor, color mask, active texture, and viewport.

## 5. Deferred Draw Queue

Raw GL draws issued by `GluePipeline` / `ShadedBufferSource` are dispatched through the internal `DeferredDrawQueue`:

- **Iris active:** Draws are deferred to `WorldRenderEvents.LAST` (after all world compositing)
- **Iris shadow pass:** Draws are silently dropped
- **Vanilla:** Draws execute immediately

## 6. Deferred lighting

An active shaderpack selects the `glue:iris_final_color` world render pipeline, and Lumos runs.
That provider publishes Minecraft's main render target (color + depth) and the matrices captured
for the level pass — the same inputs as the non-Iris `glue:main_target` path — with **no material
frame**, because an active pack owns an arbitrary colortex layout and there is nothing to capture
terrain material from. Lumos therefore estimates reflectance from the already-lit scene color for
every surface.

This is an approximation, not a proven-coherent frame. The pack may already have applied exposure,
bloom, or tonemapping to the color Glue reads and writes, and the pack's own scene depth may differ
from Minecraft's main depth attachment.

Iris installed with shaders disabled continues through `glue:main_target` with full material
capture. See [World Render Pipelines](world-render-pipelines.md) for the runtime matrix, extension
API, and conflict checklist.

## API Reference

| Method | Description |
|---|---|
| `RenderCompat.HAS_IRIS` | Whether Iris/Oculus is loaded |
| `RenderCompat.isIrisShaderEnabled()` | Whether a shader pack is active |
| `RenderCompat.isRenderingShadowPass()` | Whether in a shadow pass |
| `RenderCompat.isIrisBypassing()` | Whether Iris is loaded and `ImmediateState.bypass` is currently set |
| `RenderCompat.assignIrisProgram(pipeline, name)` | Register pipeline with Iris |
| `RenderCompat.withIrisBypass(action)` | Run action with `ImmediateState.bypass = true` |
| `RenderCompat.withIrisFullBypass(action)` | Run action with both `bypass` and `safeToMultiply` |
| `RenderCompat.getIrisMainDepthGlId()` | Get the main depth texture GL ID from Iris |
| `RenderCompat.getIrisSceneDepthGlId()` | Get the scene depth (noHand/noTranslucents) GL ID |
| `RenderCompat.getIrisRenderTargetArray()` | Get all Iris render target objects via reflection |
| `RenderCompat.getIrisTargetTextures(target, name)` | Extract `[mainId, altId, width, height]` from a render target |

## Implementation Details

- `RenderCompat` — public API, safe to call without Iris
- `IrisProxy` — package-private, isolated Iris class loading (only loaded when Iris is present)
- All Iris reflection is centralized in `IrisProxy` and memoized
- `FramebufferHelper.getFramebufferId()` — gets GL FBO ID via `GlTexture.getFbo()`, same mechanism as Iris's `iris$bindFramebuffer()`
