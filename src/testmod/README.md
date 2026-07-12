# Glue Test Mod

A runnable mod (`glue-test`) that exercises every public Glue feature in-game.
Each demo is intentionally small and maps to one library feature so it can be
read as living documentation. Entry point: **`TestmodClient`** — it registers
everything in `onInitializeClient()`.

## Feature → file index

| Glue feature | Demonstrated by |
|---|---|
| `BlocksRegistry` | `registries/TestBlocks.java` |
| `ItemsRegistry` (block items + custom item) | `registries/TestItems.java` |
| `ItemGroupsRegistry` (creative tab) | `registries/TestItemGroups.java` |
| `BlockEntitiesRegistry` | `registries/TestBlockEntities.java` |
| `BlocksRendererRegistry` (cutout layer) | `registries/TestBlocksRenderer.java` |
| `DataComponentTypesRegistry` + `TransformationComponent` | `registries/TestDataComponents.java`, `items/TestComponentItem.java` |
| `KeybindingsRegistry` | `registries/TestKeybinds.java` |
| `CoreShaderRegistry` (raw pipeline + `GluePipeline`) | `registries/TestShaders.java` |
| `PostShaderRegistry` / `TimedEffectRegistry` | `registries/TestShaders.java` |
| `LightManager` / `LightRenderer` (deferred colored lights) | `registries/TestLighting.java` |
| `GlueBlock` + data-driven outline | `blocks/demo/TestOutlineBlock.java` (+ `glue/outlines/example.json`) |
| `GlueVoxelShape` | `blocks/demo/TestOutlineBlock.java` |
| `VoxelShaper` (directional shapes) | `blocks/demo/TestShapeBlock.java` |
| `IHaveBigOutline` (oversized selection box) | `TestOutlineBlock`, `TestShaderBlock`, `TestSpinningBlock` |
| `GlueTransformStack` (fluent transforms) | `render/block/entity/TestOutlineBlockEntityRenderer.java` (basic), `TestSpinningBlockEntityRenderer.java` (advanced `then()`) |
| `GluePipeline` + `ShadedBufferSource` (entity shader capture) | `TestShaderBlockEntityRenderer.java`, `TestAdditiveSpriteBlockEntityRenderer.java` |
| Data-driven `GluePipeline` loading | `AdditiveSpriteRenderer.java` (+ `glue/pipelines/*.json`) |
| Cycling all registered pipelines | `render/TestShaderPipelines.java` (used by the shader block) |
| Post-processing effects (toggle + timed) | `render/TestPostShaderHandler.java`, `render/PostEffectDebugHud.java` |
| Light inspector (list / edit / in-world preview) | `render/LightDebugHud.java`, `render/LightShapePreviewRenderer.java` |

## Demo blocks

| Block | Shows | Block entity |
|---|---|---|
| `test_outline` | data-driven outline, `GlueVoxelShape`, transform stack | `TickingBlockEntity` |
| `test_spinning` | animated orbit rendering via transform stack | `TickingBlockEntity` |
| `test_shader`  | cycle an item through every `GluePipeline` (right-click) | `TestShaderBlockEntity` (stateful) |
| `test_additive_sprite` | additive-blended sprite via `ShadedBufferSource` | `TickingBlockEntity` |
| `test_shape`   | `VoxelShaper` directional shapes (right-click cycles) | — (no entity) |

Blocks that only need an animation clock share **`TickingBlockEntity`**; only
`test_shader` carries persistent state, so it keeps a dedicated entity.

## Post-processing: the three layers

Post effects flow through three data-driven layers. They are **not** redundant —
each is a distinct stage. One end-to-end example (`departure_vortex`) threads all
three:

```
glue/post_effects/<name>.json   TimedEffectDefinition  — duration / curve / UBO,
                                 references a post chain by id
        │  resolved from the POST_CHAINS registry
        ▼
glue/post_chains/<name>.json     PostChainDefinition → PostShaderHandle
                                 (optional external_targets; default = main target)
        │  handle id points at a vanilla chain of the same name
        ▼
post_effect/<name>.json          vanilla Minecraft PostChain — passes, uniforms, inputs
        │  references
        ▼
shaders/post/<name>.fsh          GLSL fragment shader
```

A post chain can also be registered **in Java** with `POST.register("name")`
instead of a `glue/post_chains/*.json` file — both produce a `PostShaderHandle`
in the same registry. The debug HUD shows one of each path:

- **Departure Vortex** — JSON timed effect → JSON post chain (full data-driven path).
- **Denial Pulse** — JSON timed effect → Java-registered chain (`end_locked_pulse`).
- **Chromatic (registry def)** — Java-defined `TimedEffectDefinition` → Java chain.
- **Chromatic / Shattered / Impact (Java)** — fully Java effects with custom
  uniform writers and curve lambdas (the escape hatch JSON can't express).
- **Blur / Grayscale** — plain on/off toggle handles (no timing).

Open the HUD with **F9**; hold **ALT** to drag it, arrows to navigate,
Enter to fire, Backspace to stop.

## Deferred lights (Phase 1)

Press **L** to drop three **static** colored point lights (red / green / blue) in
a ring around you, plus a warm **spot** "flashlight" that follows your view. They
illuminate existing world geometry via a screen-space deferred pass
(`LightRenderer`, hung off `POST_WORLD_RENDER`): reconstruct world position from
the scene depth buffer, derive edge-aware normals from depth (5-tap), accumulate
colored `N·L` falloff into an **HDR (RGBA16F)** buffer, then composite with a
Reinhard tonemap so bright/overlapping lights roll off instead of clipping.

**Every light casts real shadows.** `LightDepthSceneRenderer` rasterises scene depth
from the light's point of view: one map for the spot, six — a cube — for each point
light, with the deferred pass run once per face so the six tile the sphere without
overlapping. The pass filters them with **PCSS** (search for blockers, estimate the
penumbra from how far in front of the receiver they sit, then filter over exactly
that width), so contact shadows stay sharp while distant ones soften.

Maps are baked in **light-relative** space, which makes them independent of the
camera and therefore **cacheable**: a light that hasn't moved costs nothing after its
first frame. That is what makes six-face point shadows affordable — expect a brief
hitch when you first press **L**, then a steady cost of zero. Casters are culled per
face and against the light's range/cone, so a face only rasterises the blocks that
can appear in it.

Light is tinted by the surface it lands on (the scene colour stands in for albedo,
since the vanilla path has no albedo G-buffer), then rolled off with a Reinhard
tonemap, so overlapping lights saturate in colour rather than blowing out to white.

Open the **light inspector** with **F12**: it lists every active light with an
in-world wireframe preview (reach sphere for points, cone for spots — the expanded
light is highlighted). Expand a light with Enter to edit its colour, intensity,
range, position and (for cones) yaw/pitch/cone angles with **←/→** (SHIFT =
coarse steps, hold to scrub); Backspace deletes it, and the bottom rows add a new
point/spot light at your position. Every edit swaps a rebuilt `Light` into the
`LightManager` — the identity-keyed shadow/glass caches see a new instance and
re-bake, which is the intended invalidation path. Hold **ALT** to drag the panel
and click rows, like the post-effect HUD.

Still to come: **entity** shadow casters (mobs don't occlude yet), invalidating a
light's map when a nearby block changes (toggle the light to re-bake), and Iris
parity. The vanilla render path is the quality target for now.
