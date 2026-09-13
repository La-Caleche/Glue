---
title: "Workshop: Light the Lumen Probe"
description: Continue the Lumen Probe by toggling one local Lumos preview light with safe identity ownership.
artifact:
  - glue-lumos
  - glue-lumos-client
modId:
  - glue-lumos
  - glue-lumos-client
environment:
  - client
---

# Workshop: Light the Lumen Probe

| Artifact | Fabric mod ID | Environment |
| --- | --- | --- |
| `glue-lumos` | `glue-lumos` | Client and server API |
| `glue-lumos-client` | `glue-lumos-client` | Client only |

## Outcome

Continue the Light Workshop after the [Rendering Milestone](./rendering.md) by placing the Lumen
Probe three blocks ahead of the player and using that point as a light preview. Press `P` once to
create exactly one local light without a real shadow map. Press `P` again to remove that same light.
The preview is client-only, is never saved, and is cleared when the client changes worlds or
disconnects.

## Add the Lumos Modules

The combined tutorial project is already client-only after the Rendering milestone. Add both Lumos
artifacts alongside the existing render dependency:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-lumos:<glue-version>")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<glue-version>")
}
```

Merge the required IDs and client environment into the application descriptor:

```json [src/main/resources/fabric.mod.json]
{
  "environment": "client",
  "depends": {
    "glue-render": "<glue-version>",
    "glue-lumos": "<glue-version>",
    "glue-lumos-client": "<glue-version>"
  }
}
```

If Light Workshop must continue to load on a dedicated server, do not add those client-only hard
dependencies to its shared descriptor. Move the client entrypoint and its `glue-render` and
`glue-lumos-client` requirements into a separate client-only companion mod instead; split source
sets alone do not change Fabric dependency resolution.

## Add the Toggle

Merge these fields, registrations, and methods into the existing `lightworkshop` client entrypoint.
Keep the rendering milestone's `MAIN_RENDER` registration beside them. The code samples the probe
position when the preview turns on; the new fields are its owner state:

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class LightWorkshopClient implements ClientModInitializer {
    private final KeyMapping togglePreview = new KeyMapping(
            "key.lightworkshop.toggle_preview_light",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.lightworkshop");

    private ClientLevel ownerLevel;
    private Light previewLight;

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(togglePreview);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(
                (client, level) -> clearPreview());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> clearPreview());
    }

    private void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            clearPreview();
            return;
        }
        while (togglePreview.consumeClick()) {
            if (previewLight == null) {
                showPreview(client);
            } else {
                clearPreview();
            }
        }
    }

    private void showPreview(Minecraft client) {
        Vec3 probe = client.player.getEyePosition()
                .add(client.player.getViewVector(1.0f).scale(3.0));
        Light definition = Light.point(
                probe.x, probe.y, probe.z,
                1.0f, 0.72f, 0.38f,
                2.0f, 8.0f).withShadow(false);

        ownerLevel = client.level;
        previewLight = Lumos.spawn(ownerLevel, definition);
    }

    private void clearPreview() {
        if (previewLight != null && ownerLevel != null) {
            Lumos.despawn(ownerLevel, previewLight);
        }
        previewLight = null;
        ownerLevel = null;
    }
}
```

Keep the existing client entrypoint in `fabric.mod.json` pointed at
`dev.example.lightworkshop.LightWorkshopClient`.

Add the key labels:

```json [src/main/resources/assets/lightworkshop/lang/en_us.json]
{
  "key.categories.lightworkshop": "Light Workshop",
  "key.lightworkshop.toggle_preview_light": "Toggle Probe Light"
}
```

## Expected Result

Enter a dim Fast or Fancy world and face an opaque wall from a few blocks away. Press `P` once. One
warm orange light pool should appear at the Lumen Probe position, three blocks ahead of the player's
eyes. Move after toggling it on: this milestone's preview stays at the sampled probe position. Press
`P` again and the pool should disappear completely. Repeated on/off cycles must never accumulate
extra lights.

<DocImage title="Expected result: one toggled Lumen Probe light" description="First frame: a dim opaque wall. After one P press: exactly one warm orange pool centered at the probe. After the second P press: the wall returns to the first frame with no leftover light." />

## Keep Owner State in One Place

`ownerLevel` and `previewLight` live in the client entrypoint instance because this preview belongs to
that client feature, not to a block, entity, or server save. `Lumos.spawn` returns the same `Light`
object supplied as `definition`; storing that return value preserves the identity needed by
`Lumos.despawn`.

The world-change and disconnect callbacks clear both Lumos state and the owner's Java fields. Lumos
also closes the old world's renderer context, but relying only on that cleanup would leave
`previewLight` non-null and make the next world look as if its preview were already enabled.

<DocImage title="Lumen Probe owner state" description="LightWorkshopClient owns ownerLevel plus one exact previewLight reference; every off, world-change, and disconnect path calls clearPreview and clears both." />

## Try Small Variants

- Change the three linear RGB values to tint the preview without changing its lifetime.
- Change `8.0f` to adjust the radius in blocks.
- Keep `withShadow(false)` while iterating; a point light otherwise needs six real shadow-map faces.
  Mapless lights may still apply approximate capsule shadows to nearby living entities.
- For a light that follows the moving probe every frame, move on to an attached spot light rather
  than despawning and rebuilding a point light every tick.

## Technical Reference

::: details Why this toggle cannot create duplicates
The client tick consumes every queued key press. The first press can call `showPreview` only while
`previewLight` is null. Every later press takes the clear branch until that exact light is despawned
and the field becomes null again. No call searches `Lumos.active`, so the workshop cannot remove a
light owned by another feature.
:::

::: details What the preview does not do
The light is a local renderer object. It is not sent to the server, written to a dimension, or shown
to another player. It does not change vanilla light values, block-light propagation, crop growth, or
mob spawning. For shared saved lighting, use the server-owned task in
[Control Light Lifetime and Persistence](../lumos/lights.md#place-a-server-owned-lamp).
:::

## Next Steps

- [Test the Lumen Probe Workshop](./testing.md) queues the `P` key, verifies the exact light
  definition and identity cleanup, and captures the visible result.
- [Control Light Lifetime and Persistence](../lumos/lights.md#attach-a-flashlight) turns the static
  probe preview into a frame-sampled flashlight.
- [Control Light Lifetime and Persistence](../lumos/lights.md#place-a-server-owned-lamp) creates a
  server-owned light that survives reloads.
- [Test Materials and Renderer Compatibility](../lumos/materials-and-compatibility.md) checks the
  preview against surfaces, graphics modes, shadows, Iris, and Sodium.
