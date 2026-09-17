---
title: Toggle a Local Light
description: Give the Lumen Probe one client-owned light with explicit input and world cleanup.
artifact: glue-lumos; glue-lumos-client
modId: glue-lumos; glue-lumos-client
environment: client
---

# Toggle a Local Light

Continue after the [probe HUD](./rendering.md). While holding the probe, **P** creates one warm light
three blocks ahead of the player's eyes. Another press removes it. Putting the probe away, changing
worlds or disconnecting also removes it.

## Add Lumos

Add these dependencies beside Core and Render:

```kotlin [build.gradle.kts]
dependencies {
    modImplementation("fr.lacaleche.glue:glue-lumos:<glue-version>")
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<glue-version>")
}
```

Add `"glue-lumos": "<glue-version>"` and `"glue-lumos-client": "<glue-version>"` to the existing
descriptor's `depends` object. Keep its client environment and both entrypoints from the previous step.

## Own the Light in One Feature

```java [src/client/java/dev/example/lightworkshop/client/ProbeLighting.java]
package dev.example.lightworkshop.client;

import dev.example.lightworkshop.registry.WorkshopItems;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.registries.KeybindingsRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class ProbeLighting {

    private ClientLevel ownerLevel;
    private Light preview;

    public void register() {
        KeybindingsRegistry keys = new KeybindingsRegistry("lightworkshop");
        keys.register("toggle_preview_light", "key.categories.lightworkshop", GLFW.GLFW_KEY_P, this::toggle);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (this.ownerLevel != null && (client.level != this.ownerLevel || !holdsProbe(client))) this.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.clear());
    }

    private void toggle(Minecraft client) {
        if (this.preview != null) {
            this.clear();
            return;
        }
        if (client.level == null || !holdsProbe(client)) return;
        Vec3 position = client.player.getEyePosition().add(client.player.getViewVector(1.0f).scale(3));
        this.ownerLevel = client.level;
        this.preview = Lumos.spawn(this.ownerLevel, Light.point(position.x, position.y, position.z,
                1.0f, 0.72f, 0.38f, 2.0f, 8.0f).withShadow(false));
    }

    private void clear() {
        if (this.preview != null && this.ownerLevel != null) Lumos.despawn(this.ownerLevel, this.preview);
        this.preview = null;
        this.ownerLevel = null;
    }

    private static boolean holdsProbe(Minecraft client) {
        return client.player != null && client.player.getMainHandItem().is(WorkshopItems.LUMEN_PROBE);
    }
}
```

Keep the existing HUD registration and add the light feature. This is the complete client entrypoint
after both steps:

```java [src/client/java/dev/example/lightworkshop/LightWorkshopClient.java]
package dev.example.lightworkshop;

import dev.example.lightworkshop.client.ProbeHud;
import dev.example.lightworkshop.client.ProbeLighting;
import net.fabricmc.api.ClientModInitializer;

public final class LightWorkshopClient implements ClientModInitializer {

    private final ProbeLighting lighting = new ProbeLighting();

    @Override
    public void onInitializeClient() {
        ProbeHud.register();
        this.lighting.register();
    }
}
```

Merge these labels into the existing language file:

```json [src/main/resources/assets/lightworkshop/lang/en_us.json]
{
  "item.lightworkshop.lumen_probe": "Lumen Probe",
  "key.categories.lightworkshop": "Light Workshop",
  "key.lightworkshop.toggle_preview_light": "Toggle Probe Light"
}
```

## Check the Result

Use Fast or Fancy graphics and face an opaque wall in a dim area. Hold the probe, press **P**, move,
then press **P** again. The light stays at its sampled position until removed; it does not follow
the player. Repeated toggles must not accumulate lights. Put the probe away or reconnect to confirm
cleanup. The binding is remappable through Minecraft's Controls screen.

`Lumos.spawn` returns the supplied immutable `Light`. `despawn` removes that exact identity; do not
reconstruct an equivalent value to remove it. `ownerLevel` also lets the feature clear its own state
after a dimension change, even when Lumos has already closed the old renderer context.

This is a local visual light, not a saved world light or a change to vanilla block light. It has no
real shadow map because of `withShadow(false)`. For a moving flashlight or shared persistent lamp,
see [Light lifetime and persistence](../lumos/lights.md).

Next: [test the light lifecycle](./testing.md).
