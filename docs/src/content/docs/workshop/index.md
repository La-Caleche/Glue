---
title: Light Workshop
description: Build a probe item, add client HUD feedback, toggle a local Lumos light and test its lifecycle.
---

# Light Workshop

This is a small **mod you create**, using the public Glue APIs. It is not a module bundled with Glue.
For existing executable examples, use the [showcase](../showcase.md).

The goal is a Lumen Probe: hold it to display a HUD, press **P** to place a warm preview light, and
press **P** again or put the item away to remove the light. A final live-client test checks cleanup.

## Before You Start

Use a working Fabric **Minecraft 1.21.8 / Java 21** project with official Mojang mappings. Complete
[Installation](../getting-started.md), then use these names consistently:

| Kind | Value |
|---|---|
| Mod ID | `lightworkshop` |
| Java package | `dev.example.lightworkshop` |
| Common entrypoint | `dev.example.lightworkshop.LightWorkshop` |
| Client entrypoint | `dev.example.lightworkshop.LightWorkshopClient` |
| Item ID | `lightworkshop:lumen_probe` |

Examples use `src/main/java` for shared code and `src/client/java` for client code. The latter assumes
Loom's split environment source sets. In a single-source-set project, put the client files under
`src/main/java` but load them only through the Fabric client entrypoint.

## The Four Steps

| Step | Result | New dependency |
|---|---|---|
| [1. Register the probe](./probe.md) | `/give` produces a named item using a vanilla texture | `glue-core` |
| [2. Add a HUD](./rendering.md) | Holding the probe shows position and target information | `glue-render` |
| [3. Toggle a local light](./lighting.md) | One light with explicit ownership and cleanup | `glue-lumos`, `glue-lumos-client` |
| [4. Test the lifecycle](./testing.md) | Two toggle cycles and a screenshot in a development testmod | `glue-gametest` |

Each step adds a separate class. The client entrypoint only wires features together; you do not
replace earlier feature implementations as the tutorial progresses. A pedestal, creative tab and
custom data component are optional extensions, not hidden prerequisites.

## Environment Boundary

The first item step is shared-side code. The rendering and lighting continuation uses a single
client-only tutorial mod for **local singleplayer development**: its descriptor becomes
`"environment": "client"` when it requires the client libraries.

For a mod that must be installed on a dedicated server, keep content registration in a shared mod
and put the required rendering integration in a client-only companion mod. Split source sets alone
do not change Fabric's mod-dependency rules. See [client dependencies](../getting-started.md#optional-client-module).

## Optional Extensions

- [Creative tabs and item data](../core/items.md).
- [A complete pedestal block](../core/blocks.md).
- [Block outlines](../rendering/block-outlines.md) and [post effects](../rendering/post-effects.md).
- [Attached or persistent lights](../lumos/lights.md).
- [Web interfaces](../web/index.md) to control a feature from HTML or React.
