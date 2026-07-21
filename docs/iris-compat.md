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
VAO, texture bindings for units 0–12, blend (with per-channel src/dst), depth test/func/mask,
cull, scissor, color mask, active texture, and viewport.

## 5. Deferred Draw Queue

Raw GL draws issued by `GluePipeline` / `ShadedBufferSource` are dispatched through the internal `DeferredDrawQueue`:

- **Iris active:** Draws are deferred to `WorldRenderEvents.LAST` (after all world compositing)
- **Iris shadow pass:** Draws are silently dropped
- **Vanilla:** Draws execute immediately

## 6. Lumos does not run under an active shaderpack

`glue-lumos` (deferred colored lights) is **entirely disabled while a shaderpack is loaded**, and
that is an architectural boundary, not a gap waiting to be filled — the pack owns its own
`colortex` layout and its own composite chain, so Glue's G-buffer has nothing coherent to attach to
or to composite over. See
[Dynamic Lights](lights.md#performance--limitations) for the reasoning.

Iris **installed with shaders switched off** is the common case and is fully supported: Iris is
dormant, `isIrisShaderEnabled()` returns false, and Lumos runs its normal vanilla Fancy path with
full material capture. Everything else on this page (pipeline registration, shadow-pass detection,
the capture pipeline, post-shader compat) is independent of Lumos and works under an active pack.

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
- Iris's **API** classes (`IrisApi`, `IrisProgram`, `ImmediateState`) are imported directly by
  `RenderCompat`. There is no proxy class: every call site short-circuits on `HAS_IRIS`, so the JVM
  never resolves them when Iris is absent. Do not add a call that touches them before that check.
- Iris **internals** (pipeline manager, render targets, texture ids) have no API and are reached
  reflectively through `ModCompatManager` — see [Mod Compatibility](mod-compat.md).
- The only memoization is a per-frame cache of the two Iris depth texture ids. `resetFrameCache()`
  must be called once per frame (e.g. `WorldRenderEvents.START`) or those ids go stale.
- `FramebufferHelper.getFramebufferId()` — gets GL FBO ID via `GlTexture.getFbo()`, same mechanism as Iris's `iris$bindFramebuffer()`
