---
title: Add Your First Lumos Light
description: Add one local colored point light, see it in game, and remove the exact light you own.
artifact:
  - glue-lumos
  - glue-lumos-client
modId:
  - glue-lumos
  - glue-lumos-client
environment:
  - client
  - server for the shared model only
---

# Add Your First Lumos Light

| Artifact | Fabric mod ID | Environment |
| --- | --- | --- |
| `glue-lumos` | `glue-lumos` | Client and server |
| `glue-lumos-client` | `glue-lumos-client` | Client only |

## Outcome

You will create one warm point light without a real shadow map three blocks in front of the player.
Only that player sees it. Turning the preview off removes the exact `Light` instance that was turned
on.

Lumos adds color to the rendered scene. It does not create vanilla block light, relight chunks,
change mob spawning, or change values queried by gameplay code.

## Spawn One Local Light

Keep the light and its client level together in the object that owns the preview:

```java [src/client/java/dev/example/lightworkshop/client/ProbePreview.java]
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ProbePreview {
    private Level ownerLevel;
    private Light previewLight;

    public void show(LocalPlayer player) {
        hide();

        Vec3 probe = player.getEyePosition()
                .add(player.getViewVector(1.0f).scale(3.0));
        Level level = player.level();
        previewLight = Lumos.spawn(level, Light.point(
                probe.x, probe.y, probe.z,
                1.0f, 0.72f, 0.38f,
                2.0f, 8.0f).withShadow(false));
        ownerLevel = level;
    }

    public void hide() {
        if (previewLight != null && ownerLevel != null) {
            Lumos.despawn(ownerLevel, previewLight);
        }
        previewLight = null;
        ownerLevel = null;
    }
}
```

Call `show(player)` from a client keybind or tool action after a world and player exist. Call
`hide()` when the action is toggled off.

## Expected Result

Stand near an opaque wall in a dim area. The wall and floor inside an eight-block radius should gain
a warm orange tint. The source has no visible bulb, does not brighten the vanilla light-level HUD,
and does not cast terrain shadow maps because the example calls `withShadow(false)`. Mapless lights
can still apply approximate capsule shadows for up to eight nearby living entities.

<DocImage title="Before and after: first local point light" description="The same dim wall without Lumos, then with one warm orange pool of light centered three blocks in front of the player." />

## Own and Remove It

`Lumos.spawn(level, light)` returns the same `Light` object it receives. Lumos deduplicates and removes
local lights by object identity, not by matching their fields. That makes these rules important:

- Store the returned `Light` in the owner, as `ProbePreview` does.
- Pass that exact object to `Lumos.despawn`; an equal-looking replacement does not remove it.
- Despawn the old instance before spawning a changed color, range, or position.
- Clear the owner's fields on dimension change or disconnect, even though Lumos also clears the old
  client world's lights and GPU caches automatically.
- Do not remove everything returned by `Lumos.active(level)`. That snapshot also contains lights
  owned by other features and synchronized by the server.

Registering a local light before a client world is active throws `IllegalStateException`. A call made
on the logical server is harmless and returns the supplied light without rendering it.

<DocImage title="Local light ownership" description="ProbePreview owns one level and one exact Light identity; hide passes that same identity to Lumos.despawn before clearing both fields." />

## Add the Modules

Use the same Glue version for both artifacts:

```kotlin [build.gradle.kts]
dependencies {
    // Shared Light and Lumos API. Safe in common code.
    modImplementation("fr.lacaleche.glue:glue-lumos:<glue-version>")

    // Deferred renderer. Add only to a client runtime or client source set.
    modImplementation("fr.lacaleche.glue:glue-lumos-client:<glue-version>")
}
```

`glue-lumos` depends on `glue-core` (mod ID `glue`). `glue-lumos-client` also depends on
`glue-render`. Declare the corresponding Fabric dependencies for the environment in which your mod
loads. A mod that loads on a dedicated server must not require `glue-lumos-client` or `glue-render`
there.

No consumer initialization call is required. Glue registers persistence and payloads from
`glue-lumos`, while `glue-lumos-client` installs the renderer, client mirror, reload handling, and
world cleanup.

See [Getting Started](../getting-started.md) for repository setup and
[Modules](../modules.md) for the full dependency map.

## Choose the Next Kind of Light

| Task | Use | Owner |
| --- | --- | --- |
| Keep a temporary light at one position | `Lumos.spawn` and `Lumos.despawn` | Client feature keeps the exact `Light` |
| Follow a player, entity, or block | `Lumos.attach` | Client feature keeps a `LightHandle` |
| Save a light and show it to everyone in a dimension | `Lumos.place`, `update`, and `remove` | Logical server keeps the assigned `long` ID |

Continue with [Control Light Lifetime and Persistence](./lights.md) for a flashlight and a
server-owned lamp. For a complete keybind milestone, use
[Workshop: Light the Lumen Probe](../workshop/lighting.md).

## Technical Reference

::: details What a Lumos frame does
When at least one light is active, the client snapshots and culls the current world's lights, updates
budgeted shadow maps, captures material information needed around visible lights, reconstructs the
frontmost visible world position from the captured frame depth, and composites colored illumination,
bloom, and tone mapping into the world image.

The pass runs in the lighting phase of `RenderEvents.POST_WORLD_RENDER`, after material capture and
before ordinary post-world listeners. Consumers should use `Lumos`; packages named `internal` are not
public extension points.
:::

::: details Graphics modes at a glance
Vanilla Fast and Fancy are supported. Fabulous is not: its translucent color and the depth Lumos
needs do not describe one reconstructable scene, so Lumos skips the entire frame. Sodium and Iris
have narrower, version-sensitive contracts described in
[Test Materials and Renderer Compatibility](./materials-and-compatibility.md).
:::

## Next Steps

- [Workshop: Light the Lumen Probe](../workshop/lighting.md) adds a safe client toggle to the sample.
- [Control Light Lifetime and Persistence](./lights.md) adds an attached flashlight and a saved lamp.
- [Test Materials and Renderer Compatibility](./materials-and-compatibility.md) explains supported
  surfaces, shadows, emissive rendering, graphics modes, Iris, and Sodium.
