---
title: Modules
description: Choose the exact Glue artifact, Fabric mod ID, and environment for a modding goal.
environment: client and server
---

# Modules

Choose the row that matches your outcome, add that artifact, and declare its exact Fabric mod ID.

## Choose by Goal

| Goal | Maven artifact | Fabric mod ID | Environment |
| --- | --- | --- | --- |
| Register content; use packets/codecs, data components, math, shapes, or history | `glue-core` | `glue` | client and server |
| Add pipelines, post effects, materials, outlines, scene tools, compatibility, or native dialogs | `glue-render` | `glue-render` | client only |
| Model, save, and synchronize Lumos lights without requiring a renderer | `glue-lumos` | `glue-lumos` | client and server |
| Render Lumos colored lights, material passes, and shadows | `glue-lumos-client` | `glue-lumos-client` | client only |
| Build screens, HUDs, overlays and widgets from web pages connected to Java | `glue-web` | `glue-web` | client only |
| Run scripted interactions, screenshots, tools, and reports in development | `glue-gametest` | `glue-gametest` | client only, development |

All six rows are published under Maven group `fr.lacaleche.glue`. Use one Glue version across the
selected set.

## Dependency Map

| Module | Direct Glue dependencies |
|---|---|
| `glue-core` | None |
| `glue-render` | `glue-core` |
| `glue-lumos` | `glue-core` |
| `glue-lumos-client` | `glue-core`, `glue-render`, `glue-lumos` |
| `glue-web` | None; Chromium implementation dependencies are embedded privately |
| `glue-gametest` | None; development use only |

`glue-showcase` consumes every library module for demos and tests. Run it from this repository;
do not add it as a production dependency. See [Runnable Examples](./showcase.md).

::: info Artifact graph versus Fabric graph
Gradle can bring direct artifact dependencies transitively, but Fabric Loader still validates mod
IDs from descriptors. Declare every Glue mod your own descriptor requires. Keep client-only IDs out
of a dedicated server's required dependency graph.
:::

::: details Public API boundary
The modules expose supported APIs outside packages named `internal`. An accessible class in an
`internal` package remains an implementation detail and may change without source compatibility.
:::

## Next Steps

- [Install Core](./getting-started.md) for the smallest shared setup.
- [Start the Core path](./core/index.md) to build game content.
- [Open the Lumos guide](./lumos/index.md) when the goal is colored dynamic lighting.
- [Open the Web guide](./web/index.md) to embed browser content.
