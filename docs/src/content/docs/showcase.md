---
title: Runnable Examples
description: Run Glue's maintained showcase and find the implementation behind each demonstration.
environment: client; shared content also loads on a dedicated server
---

# Runnable Examples

`glue-showcase` is the repository's executable example mod. It uses the same library modules you
depend on, contains the native integration tests, and is not published as a library dependency.

The [Light Workshop](./workshop/index.md) is a tutorial mod you create yourself. For existing, runnable
code, start here.

## Run the Client

Use Java 21, Node 22.12+ and pnpm 11.5.2. Repository access is described in
[Installation](./getting-started.md#_1-add-repository-access). From the Glue repository root:

::: code-group
```bash [Unix]
./gradlew :glue-showcase:runClient
```
```powershell [PowerShell]
.\gradlew.bat :glue-showcase:runClient
```
:::

The showcase builds its React/Vite pages automatically. Library-only builds do not need Node.
Client files and worlds live under `run/`; the dedicated-server profile uses `run-server/`.

## Web and Scene Examples

Press **F6** to open the hub. Join a world for the scene, inventory and gameplay HUD examples.

| Example | Entry point | Source under `glue-showcase/` |
|---|---|---|
| React hub | F6 or `/web hub` | Java `testmod/web/hub/`; frontend `web/src/hub/` |
| Plain HTML/JS input lab | `/web lab` | Java `testmod/web/lab/`; `web/public/lab.html` |
| Vitals and native hotbar slots | `/web hud` | Java `testmod/web/hud/PlayerVitals.java`; `web/src/hud/` |
| Native terrain behind a web minimap | `/web minimap` | Java `testmod/web/hud/Minimap.java`; `web/src/minimap/` |
| Notifications above screens | `/web toast <message>` | Java `testmod/web/toast/`; `web/src/toasts/` |
| Inventory widget | Open the survival inventory | Java `testmod/web/inventory/`; `web/src/inventory/` |
| Waypoints and stacked dialogs | `/web waypoints` | Java `testmod/web/waypoint/`; `web/src/waypoints/` |
| Browser with a native toolbar | `/web browser [url]` | Java `testmod/web/browser/BrowserScreen.java` |
| Orbit camera | `/showcase scene orbit` | Java `testmod/scene/BlockSceneTestScreen.java` |
| Free-flight camera | `/showcase scene fps` | Java `testmod/scene/FpsViewportTestScreen.java` |
| Picking, gizmos and history | `/showcase scene gizmo` | Java `testmod/scene/GizmoTestScreen.java` |

Java paths in the table start at `src/main/java/fr/lacaleche/glue/`. Scene transformations affect only
the preview; Escape returns to the previous screen. In FPS mode it first releases the captured mouse.

## Rendering and Lighting

- `/showcase lights flashlight`, `ring`, `spot`, `clear`: local Lumos examples.
- `/showcase effects blur` and `grayscale`: toggle post effects.
- `/showcase effects chromatic`, `shattered`, `impact`: Java-defined timed effects.
- `/showcase effects chromatic-registry`, `vortex`, `pulse`: resource-defined timed effects.
- `/showcase raycast` or **R**: inspect normal and oversized block hits.

The showcase creative tab contains the demo blocks and the **Transform Preset Tool**. Their
registrations live under `testmod/registries/`; block renderers under `testmod/render/block/entity/`.
`testmod/lumos/DemoLights.java` demonstrates light ownership and attachment.

## Run an Integration Scenario

Use an existing disposable singleplayer world. For example:

::: code-group
```bash [Unix]
./gradlew :glue-showcase:runClient '-Pglue.gametest=glue-test:web' '-Pglue.showcase.quickplay=My Test World' '-Pcaldle.useDevAuth=false'
```
```powershell [PowerShell]
.\gradlew.bat :glue-showcase:runClient '-Pglue.gametest=glue-test:web' '-Pglue.showcase.quickplay=My Test World' '-Pcaldle.useDevAuth=false'
```
:::

| Scenario | Coverage |
|---|---|
| `glue-test:web` | Web hosts, native input, actions, state, slots, stacked screens and cleanup |
| `glue-test:web-sites` | Opt-in external-site checks; requires internet access |
| `glue-test:web-startup` | Chromium preload indicator across menus and gameplay |
| `glue-test:scenes` | Orbit/FPS/gizmo previews and owned render-target cleanup |
| `glue-test:native-dialogs` | OS dialogs; requires a person to cancel each dialog |
| `glue-test:viewport-sky` | Live-game viewport composition |
| `glue-test:iris-hud`, `glue-test:lumos-smoke`, `glue-test:albedo-issue`, `glue-test:glass-quality`, `glue-test:spot-perf` | Rendering, lighting and compatibility regressions |

The rendering scenarios that toggle shaderpacks require `-Pglue.showcase.iris=true`. The web and
scene-preview scenarios also run without Iris; use `-Pglue.showcase.iris=false` and
`-Pglue.showcase.sodium=false` for vanilla.

Reports and screenshots are written to `run/screenshots/gametest/<namespace>_<path>/`. Read the report's
`RESULT` and inspect captures: a successful Gradle launch does not prove a test passed.
See [Reports and automation](./gametest/reports.md).
