# Glue Showcase

A runnable module (`glue-showcase`) that exercises every public Glue feature in-game. Its content
continues to use the `glue-test` resource namespace to avoid an unrelated asset migration.
Each demo is intentionally small and maps to one library feature so it can be
read as living documentation. Entry points: **`TestmodClient`** registers every
client demo in `onInitializeClient()`; **`Testmod`** (both sides) opens the Lumos
client request channel to operators, which is what lets the light HUD edit world
lights. The module runs on both sides: `:glue-showcase:runClient` and
`:glue-showcase:runServer`.

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
| `Lumos` (visual lights + server-owned world lights) | `lumos/DemoLights.java`, `Testmod.java` |
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

## Deferred lights (`glue-lumos`)

Press **F11** to spawn the demo scene: three **static** shadowed point lights
plus a 24-light unshadowed stress ring (mouse button 5 spawns a warm **spot**
along your view). **K** toggles a flashlight — a spot attached to the player's
eyes via `Lumos.attach`, re-sampled every rendered frame; pressing again
restyles it in place through `LightHandle.light()`, cycling colors before
turning off. These are *visual* lights — `Lumos.spawn`, this client only,
gone with the session. They illuminate
existing world geometry via a screen-space deferred pass
(`LightRenderer`, hung off `POST_WORLD_RENDER`): reconstruct world position from
the scene depth buffer, derive edge-aware normals from depth (5-tap), accumulate
colored `N·L` falloff into an **HDR (RGBA16F)** buffer, then composite in linear
space with exponential rolloff so bright/overlapping lights do not hard-clip.

Shadow-enabled lights use real maps. `LightDepthSceneRenderer` rasterises scene
depth from the light's point of view: one map for the spot, six — a cube — for each
point light. The pass filters them with **PCSS** (search for blockers, estimate the
penumbra from how far in front of the receiver they sit, then filter over exactly
that width), so contact shadows stay sharp while distant ones soften. The stress
ring uses `withShadow(false)` to demonstrate the lower-cost many-light path.

Maps are baked in **light-relative** space, which makes them independent of the
camera and therefore **cacheable**: a light that hasn't moved costs nothing after
its first bake. Separate resident-map and per-frame update budgets spread initial
and invalidated bakes across frames. Loaded non-empty chunk sections are scanned
once per point light, then the resulting casters are culled and reused for all six
faces.

Light is tinted by the surface it lands on (the scene colour stands in for albedo,
since the vanilla path has no albedo G-buffer), then exponentially rolled off in
linear space so overlapping lights saturate in colour rather than blowing out.

Open the **light inspector** with **F12**: it lists every active light with an
in-world wireframe preview (reach sphere for points, cone for spots — the expanded
light is highlighted). Expand a light with Enter to edit its colour, intensity,
range, position and (for cones) yaw/pitch/cone angles with **←/→** (SHIFT =
coarse steps, hold to scrub); Backspace deletes it, and the bottom rows add a
visual point/spot or **place a world light** ahead of you. Every edit swaps a
rebuilt `Light` through `Lumos` — the identity-keyed shadow/glass caches see a
new instance and re-bake, which is the intended invalidation path. Hold **ALT**
to drag the panel and click rows, like the post-effect HUD.

World lights show as cyan **`W<id>`** rows: server-owned, saved with the
dimension, synced to every player, back on reload. The HUD edits them through
the Lumos client request channel — every place/edit/delete is validated
server-side (operator permission level 4, near the player, well-formed, under
the dimension cap) and takes effect when the server syncs it back. `Testmod`
opts the showcase server into that channel; it is closed by default.

The additive sprite block-entity renderer also draws a white core through
`EmissiveMaterial.unshaded`, demonstrating a self-lit custom material alongside
the existing additive shader pass.

Still to come: **entity** shadow casters (mobs don't occlude yet) and Iris parity.
Nearby block changes already invalidate affected light maps. The vanilla render path is the quality
target for now.
