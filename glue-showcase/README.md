# Glue Showcase

A runnable module (`glue-showcase`) that exercises every public Glue feature in-game. Its content
continues to use the `glue-test` resource namespace to avoid an unrelated asset migration.
Each demo is intentionally small and maps to one library feature so it can be
read as living documentation. Entry points: **`Testmod`** (both sides) registers the
synced content — blocks, items, data components, block entities, creative tab — and
opens the Lumos client request channel to operators, which is what lets Glue Studio
edit world lights; **`TestmodClient`** registers every client-only demo in
`onInitializeClient()`. The module runs on both sides: `:glue-showcase:runClient` and
`:glue-showcase:runServer`.

## Controls

Press **F6** by default to open the MCSX-based Glue Showcase control center. It launches the FPS and orbit
cameras, transform gizmo, MCSX playground, expedition planner, native file-dialog example, and Glue
Studio. The same screen also owns the flashlight, light stress ring, and warm spot-light actions,
avoiding a separate global binding for every example.

Every showcase UI, HUD, and Studio pane uses the same neutral MCSX default theme. The
legacy blue/slate playground and amber Studio palettes are no longer separate theme paths; Studio's
resource theme only makes the dock background transparent where its live world viewport requires it.

Showcase-owned menus use `UiScreen`; Back actions restore the previous hosted screen, while
transitions that intentionally resume gameplay dismiss the screen stack. The FPS, orbit, and gizmo
previews remain ordinary `AbstractViewportScreen` implementations and close back to gameplay. This
is separate from Studio's `GameViewport`: Studio confines the live world to a dock pane, whereas the
scene previews render their own isolated camera and texture.

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
| MCSX screens, playground, expedition HUD, isolated scene previews, and Glue Studio dockspace (all through F6) | `controls/`, `file/`, `scene/`, `mcsx/` (+ `assets/glue-test/mcsx/**`) |

## Scripted client tests

The showcase registers all scripted scenarios with the shared `glue-gametest` runner. MCSX drivers
live under `gametest/mcsx/`; the other scenarios live in `gametest/ShowcaseGameTests.java`.

Run one against an existing singleplayer world:

```shell
./gradlew :glue-showcase:runClient -Pglue.gametest=glue-test:mcsx-demo '-Pglue.showcase.quickplay=New World'
```

<details>
<summary>PowerShell</summary>

```powershell
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:mcsx-demo' '-Pglue.showcase.quickplay=New World'
```

</details>

The available test ids are `glue-test:mcsx-demo`, `glue-test:mcsx-expedition`,
`glue-test:mcsx-studio`, `glue-test:mcsx-axiom`, `glue-test:mcsx-lifecycle`,
`glue-test:native-dialogs`, `glue-test:iris-hud`, `glue-test:lumos-smoke`, `glue-test:albedo-issue`,
`glue-test:glass-quality`, `glue-test:spot-perf`, `glue-test:viewport-sky` and
`glue-test:showcase-smoke`.
The Axiom scenario requires Axiom 5.4.x in the run profile. Reports and screenshots are written under
`run/screenshots/gametest/<namespace>_<path>/`.

Every scenario outside `gametest/mcsx/`, except `glue-test:native-dialogs`, toggles the shaderpack through `glue-gametest`'s
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
in the same registry. Glue Studio's **Effects** pane shows one of each path:

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

Use the **F6** control center to spawn the demo scene: three **static** shadowed point lights
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

The **Glue Studio** action in the F6 control center opens (`mcsx/studio/`), the `glue-mcsx-dock` demo: editor chrome pinned around a
live game **Viewport**. The game genuinely renders inside that pane — `GameViewport` confines the
world pass to the pane's rectangle at its own resolution and aspect, and maps the HUD into it too, so
the hotbar and crosshair sit with the world they belong to while the dock keeps the rest of the frame
at full resolution. Every light you add and every post effect you fire is visible immediately, in
place. Move or resize the pane and the game follows it.

Its eight panes drive the showcase itself rather than a mock document: the **Viewport**, a **Lights**
list, an **Inspector** that restyles the selected light and re-bakes its shadow map, a **Create** pane
covering both light kinds, an **Effects** pane wired to every post-effect registration path, a
**Blocks** palette, a **Radar** plotting every emitter with its real colour and range, and a
**Console**. World lights remain server-owned, saved with the dimension and synced to every player;
`Testmod` opts the showcase server into the validated Lumos request channel.

Clicking the viewport (or **Fly**) hands the player back to you while the workspace stays open, and
Escape releases. Ungrabbed, ModernUI receives keys first and only the keys it consumes stay in the
workspace; unhandled bindings continue through Minecraft, so movement and ordinary mod keybinds work
without grabbing the cursor. Idle Escape opens the pause screen and never closes Studio; F6 closes
the showcase-owned Studio and returns to the control center. Handing focus over is not emulated: `ViewportController` only decides who owns the
input, through `GameFocus`; vanilla then reads its own bindings, runs `handleKeybinds` and turns the
player from the grabbed cursor, so every binding — rebound ones included — behaves as it does outside
the workspace. Screens opened while flying — chat, the inventory — lay out inside the viewport pane
and the workspace around them stays fully interactive; closing them returns to mouselook.

`StudioSession` owns the whole model: it mirrors the demo lights, synced world lights, post-effect
state and player pose into reactive values on the client tick, and marshals every edit back onto the
client thread. The radar is a plain ModernUI `View` that reads the workspace's own `Value<Theme>`, so
a resource reload restyles it with the rest of the dock instead of repainting hardcoded colours.

Entities participate in shadow-map rendering, and nearby block changes invalidate affected light maps.
An active Iris shaderpack uses Lumos's reduced compatibility path; full parity with the vanilla render
path remains future work.
