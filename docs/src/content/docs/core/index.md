---
title: Glue Core
description: Follow a beginner path from one registered item to blocks, data, controls, shapes, math, and undo history.
artifact: glue-core
modId: glue
environment: client and server
---

# Glue Core

Start with a Lumen Probe you can hold, then add only the Core capabilities the Light Workshop
needs. The first two steps produce a working result before introducing registry variants or data
lifecycle details.

## The Learner Path

| Step | Outcome | Guide |
| --- | --- | --- |
| 1 | Glue resolves and the mod launches | [Getting Started](../getting-started.md) |
| 2 | `/give` produces a named, textured Lumen Probe | [Lumen Probe Workshop](../workshop/probe.md) |
| 3 | The probe appears in a creative tab and carries immutable custom data | [Items and Data Components](./items.md) |
| 4 | An optional pedestal places, drops, and has a matching block item | [Blocks and Block Entities](./blocks.md) |
| 5 | One remappable key toggles a client action | [Keybindings](./keybindings.md) |
| 6 | Directional shapes, vector math, deterministic seeds, and undo/redo support tools | [Core Utilities](./utilities.md) |

Read [Registries](./registries.md) when you want to understand why the first item registration
works, or when you need block entities, particles, menus, or client resource registries.

<DocImage title="Glue Core learner path" description="A left-to-right diagram showing Light Workshop launch, Lumen Probe item, optional pedestal block, remappable probe key, and a final tools stage for shapes, math, and undo history." />

## Keep Code on the Correct Side

Most `glue-core` APIs are common code and can load on a client or dedicated server. Register items,
blocks, block entities, data components, particles, and menu types from the common initializer.

`KeybindingsRegistry` is the deliberate exception: it is packaged in `glue-core` but imports client
classes and is annotated client-only. Use it only from a client entrypoint. Rendering helpers live
in the separate client-only `glue-render` artifact.

::: details Registration lifecycle in one paragraph
The Core wrappers call Minecraft's built-in registries immediately. A class with static registered
fields must therefore be initialized during the appropriate Fabric entrypoint, once and before
registry use. The wrapper does not queue a deferred registration phase.
:::

## Next Steps

- [Build the Lumen Probe](../workshop/probe.md) for the shortest working path.
- [Register more content](./registries.md) after the first item succeeds.
- [Choose another Glue module](../modules.md) when the feature leaves Core's scope.
