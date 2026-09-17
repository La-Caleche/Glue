---
title: Optional-mod Compatibility
description: Choose supported Iris guards and optional-API fallbacks without depending on renderer internals.
artifact: glue-render
modId: glue-render
environment: client
---

# Optional-mod Compatibility

This guide produces a shadow-safe custom renderer and a clear decision about where compatibility
belongs. Use it when a Light Workshop render path must work with Iris installed, disabled, or absent,
or when another optional mod cannot be linked directly.

Glue targets Iris on Fabric. Oculus is not a supported platform or compatibility target. Sodium is
optional and has no public Glue compatibility API.

## Make the Consumer Decision First

| Your code is doing... | Consumer action |
| --- | --- |
| Drawing through `GluePipeline` | Let Glue manage Iris capture and assignment; guard a custom entity, block-entity, or particle callback against the shadow pass. |
| Applying through `GluePostEffectRenderer` | Let Glue manage framebuffer transfer, bypass, state restoration, and shadow skipping. |
| Choosing a genuinely different visual fallback for an active shader pack | Query `RenderCompat.isIrisShaderEnabled()`. |
| Avoiding a second custom draw in Iris's shadow pass | Query `RenderCompat.isRenderingShadowPass()`. |
| Reading Iris framebuffer IDs or implementation objects | Do not. Those public methods implement Glue itself and are not consumer extension points. |
| Calling a small API from another optional mod without hard linkage | Use the advanced `ModCompatManager` recipe with a safe fallback. |

<DocImage title="Renderer compatibility decision tree" description="A decision tree routing Glue pipeline and post-effect users to managed integration, custom renderer callbacks to a shadow-pass guard, and raw Iris framebuffer access to a stop sign labeled internal." />

Replace this placeholder with a 1400x650 flowchart. Use green paths for managed APIs, orange for the
supported `RenderCompat` checks, and red for borrowed Iris targets and private implementation access.

## Guard a Custom Renderer

Minecraft can call a block-entity renderer during an Iris shadow pass. If the Light Workshop display
should appear only in the normal scene, return before submitting it:

```java
import fr.lacaleche.glue.compat.RenderCompat;

@Override
public void render(/* current BlockEntityRenderer parameters */) {
    if (RenderCompat.isRenderingShadowPass()) return;

    renderPedestalDisplay();
}
```

With Iris absent or no shadow pass active, the method returns `false` and the display renders
normally. With an active shadow pass, the custom submission is skipped rather than drawn into the
shadow map and potentially rendered twice.

<DocImage title="Shadow-pass guard result" description="A before-and-after Iris comparison where the unguarded pedestal display appears as a duplicate or unwanted shadow artifact and the guarded display appears once in the normal scene." />

Replace this placeholder with matched 1000x700 Iris captures. Circle the duplicate artifact in red
on the left and label the clean right image `isRenderingShadowPass()`.

## Let Managed APIs Do Their Work

Do not wrap a normal `GluePipeline` or `GluePostEffectRenderer` call in custom Iris bypass or raw
framebuffer code. Pipelines assign their configured Iris program, capture when required, and
composite at Glue's post-world seam. Post handles save touched GL state, copy between the active Iris
and main targets when required, and process under bypass.

The remaining consumer check is active-pack state:

```java
if (RenderCompat.isIrisShaderEnabled()) {
    renderShaderPackFallback();
} else {
    renderNormalPath();
}
```

This tests whether a pack is in use, not merely whether Iris is installed. Prefer one managed path
when both branches can produce the same result.

::: details Supported RenderCompat consumer surface

| API | Fallback without Iris | Iris behavior |
| --- | --- | --- |
| `HAS_IRIS` | `false` | Class-load-time installation probe. It is not active-pack state. |
| `isIrisShaderEnabled()` | `false` | Calls `IrisApi.isShaderPackInUse()`. |
| `isRenderingShadowPass()` | `false` | Calls `IrisApi.isRenderingShadowPass()`. |
| `isIrisBypassing()` | `false` | Reads Iris immediate-state bypass. |
| `assignIrisProgram(pipeline, name)` | No-op | Assigns the exact `IrisProgram` enum name; invalid names and other `Exception`s are logged and suppressed. |
| `withIrisBypass(action)` | Runs the action | Saves the bypass flag, enables it, and restores it in `finally`. |
| `withIrisFullBypass(action)` | Runs the action | Also saves, enables, and restores safe multiplication. |

The wrappers are nesting-safe and do not swallow an exception or error from the action. Restoration
runs first, then the failure propagates.

The three state queries are direct guarded calls. They are not catch-all fallbacks for arbitrary
Iris linkage or runtime failures after the installed-mod guard succeeds.

Pipeline builder `irisProgram`, pipeline JSON `iris_program`, and the Iris-aware raw-pipeline
registration overload assign the program for you.

`HAS_IRIS` currently also checks the legacy `oculus` mod ID. That branch does not provide an Oculus
API fallback and does not make Oculus supported. Treat installation state as reliable on the
supported Iris Fabric platform only.
:::

## Do Not Build on Glue's Iris Internals

`RenderCompat` also exposes public methods used by Glue's own renderer. They are not supported
consumer extension points:

::: details Glue-internal integration methods

| Method | Internal contract |
| --- | --- |
| `resetFrameCache()` | Invalidates Glue's reflected depth IDs. Glue already calls it at `WorldRenderEvents.START`. |
| `getIrisMainDepthGlId()` | Returns `-1` or a borrowed frame-cached Iris depth texture ID. |
| `getIrisSceneDepthGlId()` | Returns `-1` or Iris's borrowed no-hand scene-depth texture ID. |
| `getIrisRenderTargetArray()` | Returns `null` or reflected Iris implementation objects. |
| `getIrisTargetTextures(target, name)` | Returns `null` or `{mainId, altId, width, height}` read from implementation fields. |

Do not retain or delete returned GL IDs, retain target objects, or add another frame-cache reset.
Texture allocation can change between frames and during pack or resource reloads.
:::

Glue guards published `IrisApi` and `IrisProgram` access by installation state. Iris implementation
layout is reflected through `ModCompatManager` behind fallbacks. Consumer code should not import Iris
immediate-state or internal classes, cast raw target objects, or call packages under
`client.render.internal`.

Sodium integration is selected by Glue's mixin plugin only when Fabric Loader reports Sodium. Its
optional injections reject unrecognized shader source instead of patching blindly. Consumer code
does not opt into or call that adapter.

## Reflect Another Optional API Only When Needed

`ModCompatManager` is a Glue Core utility with no client types, but the class requested through it
can still be client-only. Keep reflection in one compatibility class and prefer the target mod's
documented public API.

::: details Advanced optional-API recipe

This probe returns `false` when the mod, singleton, method, or expected primitive result is absent:

```java
boolean overlaysEnabled = ModCompatManager.probeInstanceBoolean(
        "examplemod",
        "com.example.api.OverlayManager",
        "getInstance", false,
        "isEnabled", false);
```

Available operations are:

```java
probeBoolean(modId, className, methodName, isPrivate)
getSingleton(modId, className, getterName, isPrivateGetter)
probeInstanceBoolean(modId, className, getterName, isPrivateGetter,
        methodName, isPrivateMethod)
invokeInstanceVoid(modId, className, getterName, isPrivateGetter,
        methodName, isPrivateMethod, paramTypes, args)
invokeInstance(modId, className, getterName, isPrivateGetter,
        methodName, isPrivateMethod, paramTypes, args, returnType, fallback)
invokeRuntime(instance, methodName, isPrivateMethod,
        paramTypes, args, returnType, fallback)
getFieldValue(instance, className, fieldName, isPrivateField,
        fieldType, fallback)
clearCache()
```

Methods with `modId` check `FabricLoader.isModLoaded` first. Boolean probes fall back to `false`,
singleton lookup to `null`, void invocation to no-op, and typed methods to the supplied fallback.
Use boxed expected types such as `Integer.class` for primitive returns.

Ordinary reflection and target `Exception`s become fallbacks. A target `Error` can propagate, so
this is not a zero-throw boundary. Private access is best effort and can be refused by the runtime.

Reflection metadata, missing lookups, and singleton results are cached process-wide. A singleton
getter that returns null stays cached until `clearCache()`. Clear only at a real lifecycle boundary,
never per frame. Do not alternate public/private flags for the same member signature because
visibility is not represented in every cache key. The caches are concurrent, but reflected methods
run synchronously on the caller's thread and retain the target API's thread rules.
:::

## Next Steps

- Use the managed path in [Rendering Pipelines](./pipelines.md#handle-iris-and-render-state).
- Review post-chain ownership in [Post-Processing Effects](./post-effects.md#respect-ordering-threads-and-iris).
- Inspect active Iris targets through the supported [Framebuffer Debug HUD](./debug-hud.md), not raw
  target IDs.
