---
title: Add a Probe HUD
description: Draw current position and target information only while the player holds the Lumen Probe.
artifact: glue-render
modId: glue-render
environment: client
---

# Add a Probe HUD

Start with the [registered probe](./probe.md). This step needs no pedestal, block entity or shader:
holding the probe displays the player's position and the block under the crosshair.

## Add the Client Dependency

Add `modImplementation("fr.lacaleche.glue:glue-render:<glue-version>")` beside Core in
`build.gradle.kts`. Use the same Glue version throughout.

For this single-project, local-development tutorial, merge these fields into `fabric.mod.json`:

```json [src/main/resources/fabric.mod.json — merge these fields]
{
  "environment": "client",
  "entrypoints": {
    "main": ["dev.example.lightworkshop.LightWorkshop"],
    "client": ["dev.example.lightworkshop.LightWorkshopClient"]
  },
  "depends": {
    "glue": "<glue-version>",
    "glue-render": "<glue-version>"
  }
}
```

Keep the existing schema, identity, Minecraft, Java and Fabric requirements. For dedicated-server
support, use a client-only companion mod instead of changing the shared mod's environment; see the
[workshop boundary](./index.md#environment-boundary).

## Register the HUD

```java [src/client/java/dev/example/lightworkshop/client/ProbeHud.java]
package dev.example.lightworkshop.client;

import dev.example.lightworkshop.registry.WorkshopItems;
import fr.lacaleche.glue.client.events.RenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ProbeHud {

    private ProbeHud() {
    }

    public static void register() {
        RenderEvents.MAIN_RENDER.register((graphics, tickDelta, width, height) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null
                    || !client.player.getMainHandItem().is(WorkshopItems.LUMEN_PROBE)) return;

            String target = client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    ? client.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString()
                    : "No block";
            graphics.drawString(client.font, "Probe: " + client.player.blockPosition().toShortString()
                    + " | Target: " + target, 8, height - 16, 0xFFFFA447);
        });
    }
}
```

Wire it once from the client entrypoint:

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import dev.example.lightworkshop.client.ProbeHud;
import net.fabricmc.api.ClientModInitializer;

public final class LightWorkshopClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ProbeHud.register();
    }
}
```

The listener reads live state and keeps no frame-owned `GuiGraphics` or world reference. Registration
is permanent for the process; do not register again on each world join or resource reload.

## Check the Result

Run the client, hold the probe, and aim at different blocks. The target name changes; selecting
another hotbar item hides the line. Coordinates are GUI-scaled, so the line stays near the bottom
at different GUI scales.

Next: [toggle a local Lumos light](./lighting.md). For a separate block feature, the
[outline guide](../rendering/block-outlines.md) explains the shared `GlueBlock` opt-in and its JSON resource.
