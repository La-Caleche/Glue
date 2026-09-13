---
title: Rendering Milestone
description: Add HUD and selected-block feedback to the Light Workshop pedestal with public Glue APIs.
artifact: glue-render
modId: glue-render
environment: client
---

# Rendering Milestone

This milestone produces two pieces of immediate feedback: an orange HUD message when the crosshair
targets the Light Workshop pedestal, and a matching orange outline around that pedestal. Use it to
verify the `glue-render` dependency and client resource loading before adding shaders.

It combines the public APIs introduced in [Rendering Events](../rendering/events.md) and
[Block Outlines](../rendering/block-outlines.md). The sample mod ID is `lightworkshop`; its package is
`dev.example.lightworkshop`.

Before continuing, complete the pedestal and keybinding Core milestones, then add `glue-render`
through the [Rendering setup](../getting-started.md#optional-client-module). In the combined tutorial
project this is the first hard client-only dependency, so the application descriptor becomes
client-only. To retain dedicated-server support, put this entrypoint and dependency in a separate
client-only companion mod.

## Opt the Pedestal into Glue Outlines

Keep the renderer ID in shared block code:

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

Define the client-side appearance with a resource:

```json [src/main/resources/assets/lightworkshop/glue/outlines/pedestal.json]
{
  "type": "simple",
  "red": 255,
  "green": 128,
  "blue": 32,
  "alpha": 0.8
}
```

## Add Target-aware HUD Feedback

Register one permanent `MAIN_RENDER` listener. It reads current client state during the callback and
does not retain `GuiGraphics`, the hit result, or the level. Merge this registration into the
existing `LightWorkshopClient.onInitializeClient()` beside the Core keybinding; do not replace that
class or its existing state.

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import dev.example.lightworkshop.block.PedestalBlock;
import fr.lacaleche.glue.client.events.RenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

public final class LightWorkshopClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RenderEvents.MAIN_RENDER.register((graphics, tickDelta, width, height) -> {
            Minecraft client = Minecraft.getInstance();
            boolean targetingPedestal = client.level != null
                    && client.hitResult instanceof BlockHitResult hit
                    && client.level.getBlockState(hit.getBlockPos()).getBlock()
                            instanceof PedestalBlock;

            String status = targetingPedestal
                    ? "Light Workshop: pedestal selected"
                    : "Light Workshop: find the pedestal";
            graphics.drawString(client.font, status, 8, height - 16, 0xFFFFA447);
        });
    }
}
```

## Check the Result

1. Start the client and join a world containing the registered pedestal block.
2. Aim beside the pedestal. The HUD says **find the pedestal**.
3. Aim at the pedestal. The message changes to **pedestal selected** and the selected shape turns
   orange.
4. Press F3+T and repeat the check. The JSON outline should reload without restarting the client.

<DocImage title="Completed rendering milestone" description="The Light Workshop pedestal under the crosshair with a translucent orange outline and the lower-left HUD text Light Workshop: pedestal selected." />

Replace this placeholder with a 1440x900 in-game screenshot. Include the crosshair, all visible
edges of the pedestal outline, and the lower-left status text. Add small callouts labeled
`lightworkshop:pedestal` and `MAIN_RENDER`.

## Verify the Contracts

- The shared `PedestalBlock` references only `GlueBlock` and `ResourceLocation`; it does not load a
  client renderer on a dedicated server.
- The event registration happens once in `ClientModInitializer`.
- The listener performs no blocking work and retains no frame-owned objects.
- A malformed outline file is reported in the client log; an unknown renderer ID falls back to
  `glue:base` rather than vanilla's outline.

## Next Steps

- Rotate a display above the block in [Transform Stack](../rendering/transforms.md).
- Shade the display with [Rendering Pipelines](../rendering/pipelines.md).
- Inspect the finished attachments with the [Framebuffer Debug HUD](../rendering/debug-hud.md).
