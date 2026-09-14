# Glue Showcase

A runnable module (`glue-showcase`) with content, rendering and lighting examples. Its content
continues to use the `glue-test` resource namespace to avoid an unrelated asset migration.
Each demo is intentionally small and maps to one library feature so it can be
read as living documentation. Entry points: **`Testmod`** (both sides) registers the
synced content — blocks, items, data components, block entities, creative tab — and
opens the Lumos client request channel to operators; **`TestmodClient`** registers every client-only demo in
`onInitializeClient()`. The module runs on both sides: `:glue-showcase:runClient` and
`:glue-showcase:runServer`.

## Controls

Press **F6** by default to open the JCEF React experiment directly.

Lighting and post-effect examples use client commands:

- `/showcase lights flashlight`, `ring`, `spot`, or `clear`.
- `/showcase effects blur` or `grayscale` toggles a steady post effect.
- `/showcase effects chromatic`, `shattered`, or `impact` triggers a Java-built timed effect.
- `/showcase effects chromatic-registry`, `vortex`, or `pulse` exercises registry-driven effects.
- `/showcase raycast` toggles raycast debugging.

Press **R** by default to toggle the raycast debug HUD independently. It compares vanilla, oversized-outline,
and final block hits, labels every candidate shape in the world, and reports linear hit distances
over the full 20-block ray.

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
| `CoreShaderRegistry` (`GluePipeline`) | `registries/TestShaders.java` |
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
| Post-processing effects (toggle + timed) | `render/TestPostShaderHandler.java` |
| Experimental JCEF/Chromium UI and native browser | `jcef/` and [`jcef-experiment`](../jcef-experiment/README.md) |

## JCEF experiment

**F6** or `/jcef demo` opens the local React fixture; `/jcef browser` opens La Calèche.
`/jcef hud` opens the read-only web HUD and `/jcef close` closes that HUD.

F3 expands the renderer metrics, F7 compares GPU/CPU channel conversion, F8 issues a pixel-correlated
round-trip probe, and F9 switches CEF's 60/30 FPS cap. Ctrl+L focuses the address bar; F5 reloads.
The first launch downloads the pinned native CEF runtime into the run profile.

`glue-test:jcef` exercises local React/native input, popups, rendering, probes and lifecycle.
`glue-test:jcef-sites` is the opt-in internet/large-surface test; its result distinguishes Google's
challenge page from successful search results. See the module's [verification and measurements](../jcef-experiment/README.md).

## Scripted client tests

The showcase registers its rendering scenarios from `gametest/ShowcaseGameTests.java` and its browser
scenarios from `jcef/JcefGameTest.java` with the shared `glue-gametest` runner.

Run one against an existing singleplayer world:

```shell
./gradlew :glue-showcase:runClient -Pglue.gametest=glue-test:jcef '-Pglue.showcase.quickplay=New World'
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:jcef' '-Pglue.showcase.quickplay=New World'
```

</details>

The available test ids are `glue-test:jcef`, `glue-test:jcef-sites`, `glue-test:native-dialogs`,
`glue-test:iris-hud`, `glue-test:lumos-smoke`, `glue-test:albedo-issue`, `glue-test:glass-quality`,
`glue-test:spot-perf`, and `glue-test:viewport-sky`.
Reports and screenshots are written under
`run/screenshots/gametest/<namespace>_<path>/`.

The JCEF scenarios run with or without Iris. The rendering scenarios in `gametest/ShowcaseGameTests.java`, except `glue-test:native-dialogs`, toggle the shaderpack through `glue-gametest`'s
built-in `glue-gametest:iris-shaders` tool (it settles the rebuilt pipeline itself, so the
scripts add no wait after it), which means those runs need Iris: add
`-Pglue.showcase.iris=true`.

## Demo blocks

| Block | Shows | Block entity |
|---|---|---|
| `test_outline` | data-driven outline, `GlueVoxelShape`, transform stack | `TickingBlockEntity` |
| `test_spinning` | animated orbit rendering via transform stack | `TickingBlockEntity` |
| `test_shader`  | cycle an item through every `GluePipeline` (right-click) | `TestShaderBlockEntity` (stateful) |
| `test_additive_sprite` | additive-blended sprite via `ShadedBufferSource`, plus an attached Lumos light | `TestAdditiveSpriteBlockEntity` (stateful) |
| `test_shape`   | `VoxelShaper` directional shapes (right-click cycles) | — (no entity) |

Blocks that only need an animation clock share **`TickingBlockEntity`**; the two that
carry state of their own keep a dedicated entity — `test_shader` its cycling index,
`test_additive_sprite` the handle of the Lumos light that follows its sprite.

Every demo block has a matching blockstate, item definition, self-drop loot table, translation, and
pickaxe mining tag. The standalone **Transform Preset Tool** stores a `TransformationComponent` on
its stack: right-click cycles readable translation, rotation, and scale presets; sneak-right-click
resets to identity. Its lore and glint make the synchronized component state visible without opening
an NBT inspector.

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
in the same registry. The `/showcase effects` commands expose each path:

- **Departure Vortex** — JSON timed effect → JSON post chain (full data-driven path).
- **Denial Pulse** — JSON timed effect → Java-registered chain (`end_locked_pulse`).
- **Chromatic (registry def)** — Java-defined `TimedEffectDefinition` → Java chain.
- **Chromatic (Java)** — the same effect built directly as a `TimedPostEffect`,
  so the two registration paths can be compared side by side.
- **Shattered / Impact (Java)** — fully Java effects with per-frame uniform
  writers (the escape hatch JSON can't express).
- **Blur / Grayscale** — plain on/off toggle handles (no timing).

The registered handles and timed definitions are exercised directly by
`TestPostShaderHandler`.

## Deferred lights (`glue-lumos`)

Use `/showcase lights ring` to spawn the demo scene: three **static** shadowed point lights
plus a 24-light unshadowed stress ring, or a warm **spot** along your view. The flashlight action
controls a spot attached to the player's eyes via `Lumos.attach`, re-sampled every rendered frame; pressing again
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

Light is tinted by the surface it lands on through the shared material G-buffer, which captures
linear albedo, normals, material id, ownership depth and material properties in the geometry pass.
It is then exponentially rolled off in linear space so overlapping lights saturate in colour rather
than blowing out.

Entities participate in shadow-map rendering, and nearby block changes invalidate affected light maps.
An active Iris shaderpack uses Lumos's reduced compatibility path; full parity with the vanilla render
path remains future work.
