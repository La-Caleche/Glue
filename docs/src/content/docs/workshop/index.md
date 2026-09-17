---
title: Light Workshop
description: Build a small Fabric mod through visible milestones, beginning with a Lumen Probe powered by Glue Core.
artifact: glue-core
modId: glue
environment: client and server
---

# Light Workshop

Light Workshop is the shared sample mod for this learning path. Its first milestone is deliberately
small: install Core, register a **Lumen Probe**, add its assets, and receive it in a running world.
The optional **Lumen Pedestal** comes after that success.

## Tutorial Identity

Use these names consistently so IDs, packages, translations, and resource paths line up:

| Kind | Value |
| --- | --- |
| Display name | `Light Workshop` |
| Mod ID | `lightworkshop` |
| Base package | `dev.example.lightworkshop` |
| First item ID | `lightworkshop:lumen_probe` |
| Optional block ID | `lightworkshop:lumen_pedestal` |

## File Conventions

Keep common registration holders under `registry`; keep item behavior under `item`; reserve
`client` for classes that a dedicated server must never load.

```text
src/main/java/dev/example/lightworkshop/
├── LightWorkshop.java
├── block/
├── item/
└── registry/

src/client/java/dev/example/lightworkshop/
└── LightWorkshopClient.java

src/main/resources/
├── fabric.mod.json
├── assets/lightworkshop/
└── data/lightworkshop/
```

Java packages use dots, registry IDs use lowercase paths, and resource folders use the mod ID as
their namespace. The `src/client/java` convention assumes Loom's split environment source sets;
if the consumer uses one source set, keep the same package and load the class only as a Fabric
client entrypoint. Do not place client imports in `LightWorkshop` or its common registry holders.

## Milestones

1. [Install Glue](../getting-started.md) and reach the title screen with both ready messages.
2. [Build the Lumen Probe](./probe.md) and obtain a named, textured item with `/give`.
3. Complete the Core foundation: [add immutable probe data](../core/items.md),
   [add the optional pedestal](../core/blocks.md), and
   [register the remappable client key](../core/keybindings.md).
4. Add the client artifact through [Rendering setup](../rendering/index.md), follow
   [Rendering Events](../rendering/events.md) and [Block Outlines](../rendering/block-outlines.md),
   then combine them in [Add Visual Feedback](./rendering.md).
5. Add the required Lumos modules and [Light the Probe](./lighting.md).
6. Complete [GameTest Setup](../gametest/setup.md) and [test the workshop](./testing.md) in a live
   development client.

<DocImage title="Light Workshop milestone map" description="A milestone diagram showing setup and the Lumen Probe leading through Core foundations, rendering feedback, Lumos lighting, and a final live-client GameTest." />

::: details Scope of the first milestone
The first probe proves dependency resolution, entrypoint initialization, Glue item registration,
resource namespacing, item model loading, translation loading, and a live client run. It does not
yet scan light, open a screen, or create Lumos lighting; later features can build on the known-good
item without changing its registry ID.
:::

::: warning Client-only milestones
The initial Core milestones can load on a client or dedicated server. The combined single-project
tutorial becomes client-only at the Rendering milestone, when it adds its first hard client-only
dependency. Keep server support by moving Rendering and the Lumos renderer into a
separate client-only companion mod, as described in each dependency step.
:::

## Next Steps

- [Build the Lumen Probe](./probe.md).
- [Review the Core learner path](../core/index.md).
- [Choose modules for later features](../modules.md).
- [Add visual feedback](./rendering.md) after the pedestal and keybinding are ready.
