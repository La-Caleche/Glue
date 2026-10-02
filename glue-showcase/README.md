# Glue Showcase

A runnable module (`glue-showcase`) with content and rendering examples. Its content
continues to use the `glue-test` resource namespace to avoid an unrelated asset migration.
Each demo is intentionally small and maps to one library feature so it can be
read as living documentation. Entry points: **`Testmod`** (both sides) registers the
synced content — blocks, items, data components, block entities, creative tab;
**`TestmodClient`** registers every client-only demo in
`onInitializeClient()`. The module runs on both sides: `:glue-showcase:runClient` and
`:glue-showcase:runServer`.

## Launch profiles

| Rendering stack | Interactive client | Client tests |
|---|---|---|
| Vanilla | `runClient` | `clientTest` |
| Sodium | `runClientSodium` | `clientTestSodium` |
| Iris + Sodium | `runClientIris` | `clientTestIris` |

Interactive clients share `../.run/client` relative to the repository root; the dedicated server
uses `../.run/server`. Their options, worlds and shaderpacks are ordinary Minecraft profile files.
Client tests instead own isolated directories under `glue-showcase/build/run/<task-name>`.
The rendering stack is chosen by the task, not a project-wide property. No periodic screenshot job
is installed during normal play: use the showcase commands, F2, or an explicit test scenario.

## Controls

Post-effect and scene examples use client commands:

- `/showcase effects blur` or `grayscale` toggles a steady post effect.
- `/showcase effects chromatic`, `shattered`, or `impact` triggers a Java-built timed effect.
- `/showcase effects chromatic-registry`, `vortex`, or `pulse` exercises registry-driven effects.
- `/showcase raycast` toggles raycast debugging.
- `/showcase scene orbit`, `fps`, or `gizmo` opens a scene preview after joining a world.

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
| `GlueBlock` + data-driven outline | `blocks/demo/TestOutlineBlock.java` (+ `glue/outlines/example.json`) |
| `GlueVoxelShape` | `blocks/demo/TestOutlineBlock.java` |
| `BlockShapeProvider` (shapes generated from models) | `datagen/ShowcaseBlockShapes.java`, `blocks/demo/TestShapeBlock.java`, `blocks/demo/TestChairBlock.java`, `blocks/demo/TestStoveBlock.java` |
| `IHaveBigOutline` (oversized selection box) | `TestOutlineBlock`, `TestShaderBlock`, `TestSpinningBlock` |
| `GlueTransformStack` (fluent transforms) | `render/block/entity/TestOutlineBlockEntityRenderer.java` (basic), `TestSpinningBlockEntityRenderer.java` (advanced `then()`) |
| `GluePipeline` + `ShadedBufferSource` (entity shader capture) | `TestShaderBlockEntityRenderer.java`, `TestAdditiveSpriteBlockEntityRenderer.java` |
| Data-driven `GluePipeline` loading | `AdditiveSpriteRenderer.java` (+ `glue/pipelines/*.json`) |
| Cycling all registered pipelines | `render/TestShaderPipelines.java` (used by the shader block) |
| Post-processing effects (toggle + timed) | `render/TestPostShaderHandler.java` |
| `BlockSceneRenderer` and `OrbitCameraController` | `scene/BlockSceneTestScreen.java` |
| `FpsCameraController`, mouse capture and entity previews | `scene/FpsViewportTestScreen.java` |
| Block picking, `GlfwGizmoController` and undo/redo | `scene/GizmoTestScreen.java`, `SceneTestController.java`, `UpdateBlockCommand.java` |

## Scene demos

Join a world, then run `/showcase scene orbit`, `/showcase scene fps` or `/showcase scene gizmo`.
They use `AbstractViewportScreen` directly, with native rendering and input. Each samples nearby
terrain into an owned render target; moving the preview camera or transforming a preview block does
not change the world or the real player. Escape returns to the screen that was open, or to gameplay.

| Example | Controls |
|---|---|
| Orbit | Left drag rotates, right drag pans, wheel zooms. `+`/`-` changes the horizontal region, Page Up/Down extends its height, Home resets camera rotation and zoom. |
| FPS | Left click captures the pointer; WASD moves, Space/Shift goes up/down, Ctrl accelerates, wheel changes speed. Escape first releases capture; another Escape returns. Right drag pans while uncaptured. |
| Gizmo | Click selects a block, drag elsewhere orbits, right drag pans, wheel zooms. `T`/`R`/`S` selects translate/rotate/scale, Tab switches local/world, `G` toggles snap, Ctrl+Z undoes, Ctrl+Y or Ctrl+Shift+Z redoes, Delete clears selection. |

`SceneTestAnchor` locates terrain even when the player is flying. The gizmo's saved transforms and
history are preview-only. Picking uses translated unit cubes, as in the original demo; rotation and
scale are not applied to the picking bounds. Each screen releases its render target and any pointer
capture on removal. See the [scene guide](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/rendering/scene-viewport.md).

## Fabric client tests

[`InventoryClientTest`](src/test/e2e/java/fr/lacaleche/glue/testmod/gametest/InventoryClientTest.java)
is a sequential `FabricClientGameTest` using the new `glue-gametest` helpers. It creates a fresh
world, opens the survival inventory through its key binding, moves items with mouse input, checks
client and server state, and captures the rendered inventory. It also checks slot coordinates after
a resize and recipe-book layout change.

```powershell
.\gradlew.bat :glue-showcase:listClientTests
.\gradlew.bat :glue-showcase:clientTest
```

The test mod lives under `src/test/e2e`, separately from the showcase's published resources.
The isolated profile and screenshots are under `build/run/<task-name>/` and are cleared on the
next run of that task. Each client task has a ten-minute timeout. The main showcase no longer depends on
`glue-gametest`; only its test source set does.

Java implementations of `FabricClientGameTest` are discovered automatically, including inherited
implementations and static nested classes. Add the class under `src/test/e2e/java`; no build map or
JSON registration is needed. `@ClientTestSpec` optionally specifies a short name or `explicitOnly`.
The default run selects all tests without that flag. `listClientTests` lists the complete suite
without opening Minecraft.

Select one or more discovered tests by short name, class name, or wildcard:

```powershell
.\gradlew.bat :glue-showcase:clientTest --tests inventory --tests scenes
.\gradlew.bat :glue-showcase:clientTestSodium --tests InventoryClientTest
.\gradlew.bat :glue-showcase:clientTestIris --tests viewport-sky
```

| Selection | Coverage / prerequisites |
|---|---|
| `inventory` | Real inventory input, client/server synchronization, resize and stale handles. |
| `scenes` | Orbit/FPS/gizmo previews opened as `/showcase scene` opens them, their controls, undo/redo and target disposal. |
| `shapes` | Every state of `test_shape` gets its generated outline and collision, on the server and the client; the Occamod chair's sixteen turns and the stove's separate collision follow their models. |
| `viewport-sky` | Full-window and inset sky views, day/night and Nether; Iris optional. |
| `native-dialogs` | Human-assisted: cancel the open/save/folder OS dialogs. |

Every client-test task copies [`src/test/assets`](src/test/assets/README.md) into its profile before
launch. Put configuration, shaderpacks, resource packs and world fixtures there using normal game
directory paths. `config/iris.properties` selects the shaderpack by its ordinary Iris filename;
the default fixture name is `shaderpacks/test-shaderpack`. Third-party packs/worlds are local,
Git-ignored assets; small configuration fixtures remain versioned. They never enter published jars.
Each task also creates its own selected test-mod jar, so two profiles in the same Gradle invocation
can select different tests without overwriting shared resources.

Tests prepare their own worlds and rendering arenas; no existing save is required. Fabric runs
selected tests sequentially and stops on the first failure. `WorldClientTest` captures failures
and closes the world; rendering scenarios clean up viewport state in `finally` blocks.
See the [helper guide](../glue-gametest/README.md) for API, registration and assertion semantics.

`auto` (the default) selects the first three scenarios in the table. The native-dialog test uses
`@ClientTestSpec(explicitOnly = true)` and requires explicit selection.
`all` or `*` includes them as well. An unknown or ambiguous selector fails before the game launches;
use the full binary class name from `listClientTests` to disambiguate a short name.

## Demo blocks

| Block | Shows | Block entity |
|---|---|---|
| `test_outline` | data-driven outline, `GlueVoxelShape`, transform stack | `TickingBlockEntity` |
| `test_spinning` | animated orbit rendering via transform stack | `TickingBlockEntity` |
| `test_shader`  | cycle an item through every `GluePipeline` (right-click) | `TestShaderBlockEntity` (stateful) |
| `test_additive_sprite` | additive-blended sprite via `ShadedBufferSource` | `TestAdditiveSpriteBlockEntity` |
| `test_shape`   | outline and collision generated from its four models, per facing (right-click cycles) | — (no entity) |
| `test_chair`   | Occamod's chair, placed in sixteen directions, its outline voxelized between quarter turns | `TickingBlockEntity` |
| `test_stove`   | Occamod's stove, outline from its drawn model and collision from a simpler one (right-click lights it) | — (no entity) |

`test_shape`, `test_chair` and `test_stove` declare no shape in Java. `.\gradlew.bat :glue-showcase:runDatagen` regenerates
`src/main/generated/glue/shapes/` from their blockstates and models; run it after changing either.

Blocks that only need an animation clock, or a renderer as the chair does, share **`TickingBlockEntity`**; two keep a dedicated
entity — `test_shader` for its cycling index, `test_additive_sprite` for its sprite animation.

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
