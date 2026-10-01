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

Press **F6** by default to open the Glue Web hub. It opens the web demos and the three native scene
previews, and toggles the web HUD, minimap and toasts.

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
| `VoxelShaper` (directional shapes) | `blocks/demo/TestShapeBlock.java` |
| `IHaveBigOutline` (oversized selection box) | `TestOutlineBlock`, `TestShaderBlock`, `TestSpinningBlock` |
| `GlueTransformStack` (fluent transforms) | `render/block/entity/TestOutlineBlockEntityRenderer.java` (basic), `TestSpinningBlockEntityRenderer.java` (advanced `then()`) |
| `GluePipeline` + `ShadedBufferSource` (entity shader capture) | `TestShaderBlockEntityRenderer.java`, `TestAdditiveSpriteBlockEntityRenderer.java` |
| Data-driven `GluePipeline` loading | `AdditiveSpriteRenderer.java` (+ `glue/pipelines/*.json`) |
| Cycling all registered pipelines | `render/TestShaderPipelines.java` (used by the shader block) |
| Post-processing effects (toggle + timed) | `render/TestPostShaderHandler.java` |
| `BlockSceneRenderer` and `OrbitCameraController` | `scene/BlockSceneTestScreen.java` |
| `FpsCameraController`, mouse capture and entity previews | `scene/FpsViewportTestScreen.java` |
| Block picking, `GlfwGizmoController` and undo/redo | `scene/GizmoTestScreen.java`, `SceneTestController.java`, `UpdateBlockCommand.java` |
| `WebScreen`, `WebHud`, `WebOverlay`, `WebWidget`, `@WebAction`, native slots | Java `web/` demo packages and the [`web/` frontend](web/README.md) |
| Signed, versioned web applications | `web/BundleDemo.java`, `assets/glue-showcase/web-bundles/` |

## Web demos

Frontend sources live in [`web/`](web/README.md). The input lab is plain HTML/JavaScript under
`web/public/`; the seven richer pages use React components and separate styles under `web/src/`,
organized by demo. A small shared hook connects React to the framework-neutral Glue bridge.

Showcase resource tasks install the pinned frontend dependencies and run Vite, so a compatible Node
version (`^20.19.0 || >=22.12.0`) and pnpm 11.5.2 are required. Output is generated under
`build/generated/webResources/assets/glue-showcase/web/` and packaged only in the showcase jar.
The client serves this generated directory directly. Run `pnpm --dir glue-showcase/web watch` from
the repository root while editing, then press F5 in a page. Library tasks never invoke Node.

Java examples are grouped under `fr.lacaleche.glue.testmod.web`: `hub`, `lab`, `browser`, `hud`,
`inventory`, `waypoint` and `toast`. `WebDemos` wires registration and entry points; their live tests
are under `src/test/e2e/java/fr/lacaleche/glue/testmod/gametest/web/`.

| Demo | Opens with | Shows |
|---|---|---|
| React hub, `index.html` | F6, `/web hub` | A `WebScreen` whose actions open the other demos and toggle the layers. |
| Vanilla input lab, `lab.html` | Hub, `/web lab` | Native input, select popups, cursors, actions with results and refusals, state and events, with no framework or compilation. |
| Vitals HUD, `hud.html` | Hub, `/web hud` | A `WebHud` replacing the hotbar and status bars. Items are native slots above the page; vanilla returns for spectators and on jumping mounts. |
| Minimap, `minimap.html` | Hub, `/web minimap` | A second `WebHud`. Java samples the terrain into a texture drawn behind the page's brass bezel, compass and waypoint pins. |
| Toasts, `toasts.html` | Actions, `/web toast <message>` | A `WebOverlay` above every screen. Toasts raised before the page connects wait in a queue. |
| Field notes, `panel.html` | Survival inventory | A `WebWidget` beside the inventory. It marks camps and opens the waypoint manager. |
| Waypoints, `waypoints.html` and `confirm.html` | Hub, field notes, `/web waypoints` | Stacked web screens: the removal dialog returns to the unchanged manager. |
| Browser | Hub, `/web browser [url]` | Vanilla toolbar widgets around a `WebWidget` that grants no bridge, even to this mod's pages. |
| Page zoom | Any focused page, Ctrl + / Ctrl - / Ctrl 0 | Each page follows the GUI scale times its own zoom. The vitals are drawn in GUI pixels and keep the default; a player zooms any page, and the choice is kept for its origin. |
| Versioned bundles | `/web bundles` | Embedded fallback, observable selection, activation/rollback controls and a deferred module. |

The two HUDs start disabled so the other showcase scenarios keep the vanilla HUD. Waypoints are kept
for the current connection only. In the browser, F3 expands delivery metrics, F9 switches the 60/30
FPS cap, F5 reloads and Ctrl+L focuses the address bar. Mod startup preloads the native runtime into
the game profile's `glue-web/` directory, with a small global progress indicator.

The `web` client test drives the original host demos with real mouse and keyboard input. `web-bundles`
checks managed resource routing, imports, service-worker refusal, reload and disposal without a remote
host. The bundle demo reads an optional `config/glue-showcase/bundles.properties` in the game profile.
Supply `channel` (an HTTPS channel URL) and `publicKey` (a Base64 SPKI Ed25519 public key, key ID
`release`) together. With no file, the demo stays offline; an invalid file fails explicitly. A test
fixture can provide the same file under `src/test/assets/config/glue-showcase/`.
See the [publication guide](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/bundles.md).
`web-sites` is
the opt-in internet test; its result distinguishes Google's challenge page from successful search
results. `web-startup` inspects the runtime indicator. See the
[library guide](https://gitlab.lacaleche.cc/loccamy/java/glue-docs/-/blob/main/src/content/docs/web/index.md).

## Scene demos

Join a world, press **F6**, then choose **Orbit scene**, **FPS scene** or **Gizmo scene**. The same
examples are available through `/showcase scene orbit`, `/showcase scene fps` and `/showcase scene gizmo`.
They use `AbstractViewportScreen` directly, with native rendering and input. Each samples nearby
terrain into an owned render target; moving the preview camera or transforming a preview block does
not change the world or the real player. Escape returns to the opening screen, or to gameplay for a
command. A web hub opens its page again when returned to from a native preview.

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
.\gradlew.bat :glue-showcase:clientTest --tests web --tests scenes
.\gradlew.bat :glue-showcase:clientTestSodium --tests InventoryClientTest
.\gradlew.bat :glue-showcase:clientTestIris --tests viewport-sky
```

| Selection | Coverage / prerequisites |
|---|---|
| `inventory` | Real inventory input, client/server synchronization, resize and stale handles. |
| `scenes` | Hub clicks, orbit/FPS/gizmo controls, undo/redo and target disposal. |
| `web` | Input lab, bridge, cursors, HUD slots, inventory widget, stacked dialogs, browser isolation and shutdown. |
| `web-bundles` | Pinned local release, module/fetch routing, service-worker refusal and reload. |
| `web-startup` | Progress indicator over menu, inventory, gameplay, hidden HUD and loading overlay. |
| `viewport-sky` | Full-window and inset sky views, day/night and Nether; Iris optional. |
| `web-sites` | Opt-in live La Calèche, Google and YouTube navigation; requires internet access. |
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

`auto` (the default) selects the first six scenarios in the table. External-site and
native-dialog tests use `@ClientTestSpec(explicitOnly = true)` and require explicit selection.
`all` or `*` includes them as well. An unknown or ambiguous selector fails before the game launches;
use the full binary class name from `listClientTests` to disambiguate a short name.

## Demo blocks

| Block | Shows | Block entity |
|---|---|---|
| `test_outline` | data-driven outline, `GlueVoxelShape`, transform stack | `TickingBlockEntity` |
| `test_spinning` | animated orbit rendering via transform stack | `TickingBlockEntity` |
| `test_shader`  | cycle an item through every `GluePipeline` (right-click) | `TestShaderBlockEntity` (stateful) |
| `test_additive_sprite` | additive-blended sprite via `ShadedBufferSource` | `TestAdditiveSpriteBlockEntity` |
| `test_shape`   | `VoxelShaper` directional shapes (right-click cycles) | — (no entity) |

Blocks that only need an animation clock share **`TickingBlockEntity`**; two keep a dedicated
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
