---
title: Control Light Lifetime and Persistence
description: Attach a flashlight, place server-owned lights, and authorize client requests safely.
artifact:
  - glue-lumos
  - glue-lumos-client
modId:
  - glue-lumos
  - glue-lumos-client
environment:
  - client
  - server
---

# Control Light Lifetime and Persistence

| Artifact | Fabric mod ID | Environment |
| --- | --- | --- |
| `glue-lumos` | `glue-lumos` | Client and server |
| `glue-lumos-client` | `glue-lumos-client` | Client only |

Start with [Add Your First Lumos Light](./index.md) if you have not yet spawned and removed one local
point light.

## Outcome

You will make a flashlight follow the player's eyes, remove it through its handle, then place a lamp
that the logical server saves and synchronizes. The two examples deliberately keep different owner
state: a client `LightHandle` for the visual effect and a server-assigned `long` ID for world data.

## Attach a Flashlight

An attachment samples position and direction every rendered frame, including partial tick. The
definition's initial transform is only a placeholder:

```java [src/client/java/dev/example/lightworkshop/client/FlashlightController.java]
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightAttachments;
import fr.lacaleche.glue.lumos.LightHandle;
import fr.lacaleche.glue.lumos.Lumos;
import net.minecraft.client.player.LocalPlayer;

public final class FlashlightController {
    private LightHandle flashlight;

    public void turnOn(LocalPlayer player) {
        turnOff();
        Light beam = Light.spot(
                0.0, 0.0, 0.0,
                0.0f, -1.0f, 0.0f,
                1.0f, 0.86f, 0.62f,
                3.0f, 24.0f,
                12.0f, 22.0f).withShadow(false);
        flashlight = Lumos.attach(
                player.level(), beam, LightAttachments.entityEyes(player));
    }

    public void turnOff() {
        if (flashlight == null) return;

        flashlight.remove();
        flashlight = null;
    }
}
```

## Expected Result

Looking around should move a warm cone smoothly with the camera instead of jumping once per game
tick. Because this example disables the real shadow map, nearby living entities can still contribute
approximate capsule shadows, including the player holding the flashlight. Anchor exclusion currently
applies only to mapped entity shadows. No other client sees the light, and it is not saved.

<DocImage title="Attached flashlight result" description="A warm cone begins at the player's eyes and follows the crosshair across a wall; turning it off leaves the original vanilla scene." />

## Own and Clean Up the Handle

The controller owns the `LightHandle`, not each frame's resolved `Light`:

- `remove()` is idempotent and permanently detaches the source.
- `isRemoved()` reports whether the handle has ended.
- `light(newDefinition)` changes color, range, cone, or shadow behavior while keeping the attachment;
  calling it after removal throws `IllegalStateException`.
- An entity attachment removes itself when the entity is removed or enters another level.
- A block attachment removes itself when its position is no longer loaded.
- A dimension change or disconnect closes every attachment in the previous client world.

`Lumos.attach` returns `null` on a server. In client code it requires an active client world, just like
`spawn`.

## Place a Server-Owned Lamp

Run persistent operations on the logical server thread. Save the returned ID in the gameplay object
that owns the lamp; otherwise the light can outlive the object with no way for it to identify the
saved entry after a restart.

```java [src/main/java/dev/example/lightworkshop/lighting/PersistentLamp.java]
package dev.example.lightworkshop.lighting;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class PersistentLamp {
    private long lightId = Lumos.NO_LIGHT;

    public void place(ServerLevel level, BlockPos pos) {
        if (lightId != Lumos.NO_LIGHT
                && Lumos.lights(level).containsKey(lightId)) return;

        lightId = Lumos.place(level, Light.point(
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                1.0f, 0.78f, 0.45f,
                2.5f, 12.0f).withShadow(false));
    }

    public void remove(ServerLevel level) {
        if (lightId == Lumos.NO_LIGHT) return;

        Lumos.remove(level, lightId);
        lightId = Lumos.NO_LIGHT;
    }

    public long lightId() {
        return lightId;
    }

    public void restoreLightId(long savedLightId) {
        lightId = savedLightId;
    }
}
```

Include `lightId()` in the owner's codec or saved data and pass it back to `restoreLightId` when that
owner loads. `Lumos.place` returns a positive ID, or `Lumos.NO_LIGHT` (`-1`) when the light is
rejected. `Lumos.update(level, id, replacement)` keeps the ID and reports whether it replaced an
existing light. `Lumos.remove(level, id)` reports whether the ID existed.

Everyone in the dimension should see the lamp after the server accepts it. It should remain after a
save and reload, and removing its owning object should remove the Lumos entry by the stored ID.

<DocImage title="World-light ownership" description="The logical server assigns a per-dimension ID, the lamp owner saves that ID, and clients only render synchronized copies." />

## Keep Client Requests Server-Authoritative

The safest design is your own narrow C2S action: validate the player's tool, target, permission, and
rate on the server, then call `Lumos.place(serverLevel, light)` there. Direct server calls do not need
the generic Lumos request channel.

Calling `Lumos.place` on a client is only an advisory request. It returns `NO_LIGHT` immediately
because clients never choose IDs. `update` and `remove` similarly return `false`; there is no request
acknowledgement. The next authoritative `Lumos.lights(level)` snapshot is the observable result.

The generic channel is closed by default. A server can opt in for operator tooling from its common
initializer:

```java [src/main/java/dev/example/lightworkshop/LightWorkshop.java]
package dev.example.lightworkshop;

import fr.lacaleche.glue.lumos.server.PersistentLights;
import net.fabricmc.api.ModInitializer;

public final class LightWorkshop implements ModInitializer {
    @Override
    public void onInitialize() {
        PersistentLights.allowClientRequests(PersistentLights.OPERATORS);
    }
}
```

`OPERATORS` requires command permission level 4. Pass a custom
`PersistentLights.ClientRequestPolicy` for a different authorization rule, or `PersistentLights.DENY`
to close the channel again.

::: danger Opening the channel writes to the world save
The policy is the authorization boundary. Lumos does not add per-mod namespaces, per-player light
ownership, per-player quotas, or an undo log. Do not allow every player just to simplify a client
tool.
:::

Accepted requests are still limited to the sender's current dimension. Adds must be within 64 blocks
of the sender. Updates require both the old and replacement positions within 64 blocks, and removals
require the old light within 64 blocks. Adds and updates reject gobos and malformed lights; adding
stops at 4,096 stored lights per dimension.

## Try Useful Variants

### Change a Flashlight Without Reattaching

```java
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightHandle;

public final class FlashlightVariants {
    public static void enableShadows(LightHandle flashlight, Light beam) {
        if (!flashlight.isRemoved()) {
            flashlight.light(beam.withShadow(true));
        }
    }
}
```

Enabling shadows makes the result more grounded but substantially increases cost. See
[Test Materials and Renderer Compatibility](./materials-and-compatibility.md#compare-shadow-map-cost).

### Follow a Block or Entity

Use `LightAttachments.block(pos)` for a loaded block center, `LightAttachments.entity(entity)` for an
interpolated entity position and view direction, or `LightAttachments.entityEyes(entity)` for its
interpolated eye position and view direction.

For a custom source, fill the reusable transform every sample and return `false` when it ends:

```java
package dev.example.lightworkshop.client;

LightHandle handle = Lumos.attach(level, definition, (world, partialTick, out) -> {
    if (!active) return false;
    out.position(x, y, z).direction(directionX, directionY, directionZ);
    return true;
});
```

Set a position every time. Also set a direction every time for spot and gobo lights. A full
`LightAttachment` implementation can override `anchorEntity()` when it follows an entity.

## Technical Reference

::: details Light factories and validation
`Light` is immutable. Colors are linear RGB values in `[0, 1]`; intensity scales the color and range
is measured in blocks.

- `Light.point(x, y, z, r, g, b, intensity, range)` creates an omnidirectional sphere.
- `Light.spot(x, y, z, dirX, dirY, dirZ, r, g, b, intensity, range, innerDeg, outerDeg)` creates a
  cone with smooth falloff between its inner and outer half-angles.
- `Light.gobo(x, y, z, dirX, dirY, dirZ, r, g, b, intensity, range, innerDeg, outerDeg,
  goboTextureId)` multiplies a spot cone by the red channel of a live GL texture.

Positions, colors, intensity, range, and directions must be finite. Intensity must be non-negative;
range must be greater than `0.05`; directions must be non-zero and are normalized. Cone half-angles
must satisfy `0 <= innerDeg < outerDeg < 89`. A gobo texture ID must be positive.

Factories do not cap maximum intensity or range. Persistent `isWellFormed()` validation caps
intensity at `64` and range at `256`. Larger factory-built values are visual-only and can still be
very expensive. Factory-built lights cast shadows by default; `withShadow(false)` returns a copy that
does not request a real shadow map. `at(...)` returns a repositioned and, when needed, re-aimed copy.
:::

::: details Gobo lifetime
A gobo ID is a live client GL texture handle. It is omitted by both light codecs and has no meaning
on a server, so persistent storage rejects `GOBO`. Rebuild the visual light if a resource reload
replaces its texture ID.
:::

::: details Persistence, synchronization, and IDs
World lights are stored per dimension in `<dimension>/data/glue_lumos_lights.dat`. Format version 1
stores a monotonic next-ID counter and each ID/light pair; the optional version field is absent while
it remains version 1. Removed IDs are not reused within a save.

The server sends a full dimension snapshot on join, dimension change, and every successful add,
update, or removal. The client drops its previous dimension view while waiting for the new snapshot.
`Lumos.lights(level)` returns a new server map or the client's read-only synchronized map. Modify
world lights only through `place`, `update`, and `remove`.

Direct server calls bypass request policy and distance checks, but still reject gobos, malformed
lights, and adds beyond the per-dimension limit.
:::

::: details Codec and untrusted-data contract
`LightCodecs.CODEC` stores NBT-compatible fields and `LightCodecs.STREAM_CODEC` stores network fields.
Both preserve type, transform, linear color, intensity, range, stored cone cosines, and the shadow
flag. Neither preserves `goboTextureId`.

Decoding reconstructs stored fields directly instead of applying factory validation. Call
`isWellFormed()` before using values decoded from an untrusted source. Normal add and update paths do
this. The current saved-state loader does not separately filter a syntactically valid file whose
numeric fields were externally corrupted. Incompatible or unparsable saved data can be replaced by
Minecraft with a fresh empty state, so back up a world before manually editing it.
:::

## Next Steps

- [Workshop: Light the Lumen Probe](../workshop/lighting.md) practices exact local ownership in a
  complete toggle.
- [Test Materials and Renderer Compatibility](./materials-and-compatibility.md) checks which surfaces
  receive light and how shadows affect performance.
- [Add Your First Lumos Light](./index.md) reviews module and environment setup.
