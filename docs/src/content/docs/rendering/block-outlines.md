---
title: Block Outlines
description: Give a selected Glue block a reloadable orange outline with a shared-code opt-in.
artifact: glue-render
modId: glue-render
environment: client
---

# Block Outlines

This task replaces the Light Workshop pedestal's vanilla selection line with a translucent orange
outline. Use it when a block needs selection feedback that differs from neighboring vanilla blocks.

## Create the Orange Outline

Add one client resource. Its path creates the renderer ID `lightworkshop:pedestal`:

```json [src/main/resources/assets/lightworkshop/glue/outlines/pedestal.json]
{
  "type": "simple",
  "red": 255,
  "green": 128,
  "blue": 32,
  "alpha": 0.8
}
```

Now opt the block into that renderer. `GlueBlock` belongs to `glue-core`, so this class remains safe
in shared code; do not import a client renderer here.

```java [src/main/java/dev/example/lightworkshop/block/PedestalBlock.java]
package dev.example.lightworkshop.block;

import fr.lacaleche.glue.block.GlueBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class PedestalBlock extends Block implements GlueBlock {
    private static final ResourceLocation OUTLINE =
            ResourceLocation.fromNamespaceAndPath("lightworkshop", "pedestal");

    public PedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation getOutlineRenderer() {
        return OUTLINE;
    }
}
```

Reload client resources with F3+T, place the pedestal, and point at it. Glue draws the block's normal
selection shape in orange. Point at a neighboring vanilla block to see the standard outline again.

## Keep Shared and Client Code Separate

The block returns only a `ResourceLocation`. Glue resolves that ID from the client outline registry
when Minecraft asks to draw the hit outline. This keeps dedicated servers from loading
`fr.lacaleche.glue.client.render.outline` classes.

A `GlueBlock` that keeps the default `getOutlineRenderer()` uses `glue:base`. An unknown ID also falls
back to that built-in black line renderer; it does not restore the vanilla implementation.

::: details JSON schema and reload behavior

The current built-in type is `simple`:

| Field | Default | Contract |
| --- | --- | --- |
| `type` | Required | Must be `"simple"`. |
| `red` | `0` | Integer color channel; keep it from `0` to `255`. |
| `green` | `0` | Integer color channel; keep it from `0` to `255`. |
| `blue` | `0` | Integer color channel; keep it from `0` to `255`. |
| `alpha` | `0.4` | Opacity; keep it from `0.0` to `1.0`. |

The codec does not range-check channels or alpha. On every client resource reload, JSON entries
replace the previous JSON layer. A JSON entry overrides a Java renderer with the same ID; removing
the file reveals the permanent Java entry again. Malformed or unbakeable definitions are logged and
skipped.
:::

::: details Register a custom Java renderer

Use Java only when the line geometry or behavior cannot be expressed by `simple` JSON:

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
private static final OutlineRendererRegistry OUTLINES =
        new OutlineRendererRegistry("lightworkshop");

@Override
public void onInitializeClient() {
    OUTLINES.register("pedestal", () ->
            new ParametricOutlineRenderer(Color.ofRGB(255, 128, 32), 0.8F));
}
```

`register` creates one permanent renderer instance immediately. It is reused across draws, so never
retain callback-owned poses, buffers, levels, or hit results in the renderer. A duplicate Java ID
logs a warning and replaces the prior Java entry.

For custom geometry, implement `GlueOutlineRenderer` or extend `SimpleBlockOutlineRenderer` and
override `renderShape`. The base simple renderer always writes alpha `0.4`; a supplied `Color` alpha
is not used. Prefer JSON or `ParametricOutlineRenderer` for color and opacity alone.
:::

## Understand the Draw Contract

Glue obtains already state-oriented geometry from `BlockState.getShape`, applies the block position,
then invokes the renderer on the client render thread. Do not return a north-only shape and expect
the outline pass to rotate it a second time. The selected block must be inside the world border and
the client must have a player.

Minecraft can request repeated outline passes in one frame. Custom renderers must be repeatable.
Glue's selection listener cancels the vanilla pass for an eligible `GlueBlock`; a renderer exception
propagates and does not trigger the `glue:base` fallback.

## Next Steps

- Add target-aware HUD feedback with [Rendering Events](./events.md).
- Animate displayed geometry with [Transform Stack](./transforms.md).
- Read [Rendering Events](./events.md#use-post-world-rendering-safely) before adding custom world
  passes around selection rendering.
