---
title: Modules
description: Choose the exact Glue artifact, Fabric mod ID, and environment for a modding goal.
environment: client and server
---

# Modules

Choose the row that matches your outcome, add that artifact, and declare its exact Fabric mod ID.
For the first Light Workshop milestone, the answer is only `glue-core`.

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

The direct Glue dependencies are intentionally short:

```text
glue-core
├── glue-render
├── glue-lumos ─────────────┐
└── glue-lumos-client ◄─────┘
         ▲
         └── glue-render

glue-gametest (independent)
glue-web (independent)
```

Read the composed rows as follows:

- `glue-render` depends on `glue-core`.
- `glue-lumos` depends on `glue-core`.
- `glue-lumos-client` depends directly on `glue-core`, `glue-render`, and `glue-lumos`.
- `glue-gametest` has no direct Glue dependency.
- `glue-web` has no direct Glue dependency; it embeds its browser implementation privately.

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
