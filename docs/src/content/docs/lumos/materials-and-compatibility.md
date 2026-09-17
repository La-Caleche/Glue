---
title: Test Materials and Renderer Compatibility
description: Check which surfaces receive Lumos light and tune shadows, emissive rendering, and renderer support.
artifact:
  - glue-lumos
  - glue-lumos-client
  - glue-render
modId:
  - glue-lumos
  - glue-lumos-client
  - glue-render
environment:
  - client
---

# Test Materials and Renderer Compatibility

Use [Add Your First Lumos Light](./index.md) for setup and
[Control Light Lifetime and Persistence](./lights.md) for ownership.

## Outcome

You will verify a light against a known-good opaque surface, understand what unsupported geometry
looks like, choose a supported graphics mode, and decide whether shadows or emissive geometry belong
in your effect.

## Make a Known-Good Surface Check

Test in a dim room with a stone wall and floor before debugging a custom translucent model. Spawn one
point light without a real shadow map in front of the wall:

```java [src/client/java/dev/example/lightworkshop/client/SurfaceCheck.java]
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class SurfaceCheck {
    private ClientLevel ownerLevel;
    private Light light;

    public void show(ClientLevel level, Vec3 lightPosition) {
        hide();
        light = Lumos.spawn(level, Light.point(
                lightPosition.x, lightPosition.y, lightPosition.z,
                0.35f, 0.65f, 1.0f,
                2.5f, 10.0f).withShadow(false));
        ownerLevel = level;
    }

    public void hide() {
        if (light != null && ownerLevel != null) {
            Lumos.despawn(ownerLevel, light);
        }
        ownerLevel = null;
        light = null;
    }
}
```

## Expected Result

On Fast or Fancy graphics, the frontmost visible stone surface should receive a smooth blue pool of
light that fades to zero at ten blocks. Its texture should remain recognizable rather than washing
to a flat white disc. No terrain shadow should appear because this diagnostic light has no real
shadow map; nearby living entities may still produce approximate capsule shadows.

If this check fails, first switch away from Fabulous, then test without an active Iris shader pack.
If stone works but custom geometry does not, check the surface coverage below instead of increasing
intensity.

## Own and Remove the Diagnostic

`SurfaceCheck` stores only the `Light` it spawned and passes that exact identity to `despawn`. Remove
the diagnostic before replacing its position or settings, and clear it when the tool closes. A client
world change also clears it automatically, but your owner should clear stale fields so the next world
can create a fresh light.

## Check the Surface

| Surface | Fast/Fancy result | Active Iris pack result |
| --- | --- | --- |
| Opaque and cutout model terrain | Captured in its normal scene draw | Nearby exposed model blocks inside visible lights are re-rendered |
| Vanilla entities | Pixel-owning opaque and cutout portions are captured | Nearby visible, non-spectator living entities are re-rendered; non-living entities are not |
| Particles | Unblended draws and opaque block-atlas terrain particles are captured | Not re-captured |
| Glass and ice | Plain and stained glass/panes, tinted glass, ice, and frosted ice use built-in capture | Same built-in material-only capture |
| Water | Potentially visible water faces use built-in capture | Same built-in material-only capture |
| Metal blocks | A curated built-in iron, gold, netherite, copper, raw-metal, and metal-ore set uses a metallic response | Same built-in material-only capture |
| Additive, no-depth, translucent-particle, and custom-shader geometry | Usually unclaimed | Unclaimed unless a reduced built-in re-render covers it |

Lumos shades only the frontmost depth surface. It cannot independently light a floor hidden behind a
depth-writing translucent surface. Thin geometry and built-in glass, water, or metal can use
depth-derived normals, so their response can be more approximate than opaque terrain or entities.
Captured particle billboards face the light for their response rather than carrying a geometric
surface normal.

There is currently no public API for registering a custom material class or renderer adapter. Making
an unsupported surface brighter cannot recover reflectance data that its render path never supplied.

## Choose a Graphics Mode

| Runtime | What to expect |
| --- | --- |
| Vanilla Fast or Fancy on Minecraft 1.21.8 | Supported full path for eligible terrain, entities, particles, and built-in special materials |
| Fabulous | Unsupported; Lumos skips the entire frame |
| Sodium `mc1.21.8-0.7.3-fabric` on Fast or Fancy | Implemented full path through a guarded terrain adapter; runtime-sensitive |
| Iris installed with shader packs disabled | Normal Fast/Fancy behavior; Sodium supplies terrain capture when present |
| Iris `1.9.6+1.21.8-fabric` with a pack active | Reduced experimental path with nearby re-capture and an approximate fallback elsewhere |

Fast is supported. Fabulous is the hard graphics-mode exclusion, including when Iris is installed.
With an active Iris pack, Lumos does not import the pack's custom PBR materials and does not promise
visual parity with the normal path.

## Compare Shadow-Map Cost

Factory-built lights request real shadow maps. The first guides use `withShadow(false)` because it
removes map bakes and map sampling while you establish correct placement and ownership. The mapless
path can still collect and sample up to eight nearby living entities as approximate capsule shadows.

- A spot light needs one shadow map.
- A point light needs six cube faces and may render nearby entity depth for each face, so it is much
  more expensive than a spot light.
- Moving shadow-casting attachments continually produce new resolved light identities and invalidate
  their cached maps.
- Large ranges scan more loaded sections for shadows and special materials.

Use lights without real shadow maps for dense, moving, or decorative sets. Add mapped shadows only
where the visible terrain occlusion is worth the frame time and GPU memory.

```java [src/client/java/dev/example/lightworkshop/client/LightBudgets.java]
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.client.render.light.LightRenderer;

public final class LightBudgets {
    public static void configure() {
        LightRenderer.setShadowBudget(8, 4);
        LightRenderer.setShadowUpdateBudget(2, 1);
        LightRenderer.setMaxLightDistance(96.0);
    }
}
```

The first pair caps resident spot/gobo maps and point cubemaps. The second limits new maps per frame.
Negative budgets clamp to zero, and lowering a resident budget immediately releases surplus maps. A
maximum distance of `0` or less follows Minecraft's effective render distance.

## Render an Emissive Source

An emissive material makes caller-rendered geometry look self-lit. It does not illuminate nearby
blocks by itself:

```java [src/client/java/dev/example/lightworkshop/client/GlowRenderer.java]
package dev.example.lightworkshop.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.render.EmissiveMaterial;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

public final class GlowRenderer {
    private static final EmissiveMaterial GLOW = EmissiveMaterial.unshaded(
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/item/amethyst_shard.png"));

    public static void render(MultiBufferSource buffers, ProbeGeometry probe) {
        VertexConsumer vertices = buffers.getBuffer(GLOW.renderType());
        probe.render(vertices, GLOW.packedLight());
    }

    @FunctionalInterface
    public interface ProbeGeometry {
        void render(VertexConsumer vertices, int packedLight);
    }
}
```

`EmissiveMaterial.shaded(texture)` stays fullbright while retaining Minecraft's directional entity
shading. `unshaded(texture)` is fully self-lit. Neither factory emits geometry, changes static block
model lighting, or creates a Lumos light.

When one caller owns both custom emissive geometry and a local attached light,
`EmissiveEmitter` can pair their lifetimes:

```java [src/client/java/dev/example/lightworkshop/client/GlowSource.java]
package dev.example.lightworkshop.client;

import fr.lacaleche.glue.client.render.EmissiveMaterial;
import fr.lacaleche.glue.client.render.light.EmissiveEmitter;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightAttachments;
import net.minecraft.core.BlockPos;

public final class GlowSource implements AutoCloseable {
    private EmissiveEmitter emitter;

    public void attach(EmissiveMaterial material, Light definition, BlockPos pos) {
        close();
        emitter = EmissiveEmitter.attach(
                material, definition, LightAttachments.block(pos));
    }

    @Override
    public void close() {
        if (emitter == null) return;
        emitter.close();
        emitter = null;
    }
}
```

`close()` removes the attached light. The emitter does not own the texture or emitted geometry;
`material()` and `light()` expose the paired objects.

## Technical Reference

::: details Uncaptured-light behavior
Lumos cannot recover exact reflectance from a color that Minecraft has already lit and fogged. The
renderer therefore distinguishes three cases:

- On a full-capture frame, an unclaimed pixel receives no Lumos overlay because
  `UNCAPTURED_LIGHT_CAP` is `0`. It keeps its existing scene appearance.
- With an active Iris pack, a pixel outside successful re-captures receives an estimated-reflectance
  overlay scaled by `0.55`. This is deliberately approximate and dimmed to avoid a white disc.
- If material capture cannot open at all, such as after a guarded shader patch is rejected, Lumos
  falls back to estimated reflectance and depth-derived normals instead of treating every pixel as a
  known capture miss.

Captured glass, water, and metal use dedicated responses rather than the full-frame uncaptured cap.
:::

Material IDs, attachment formats and shader-source patches are implementation details. Use the
[framebuffer debug HUD](../rendering/debug-hud.md) to inspect captured data rather than depending on
its packing or borrowing its GL objects.

::: details Sodium and Iris failure contracts
The repository's exact development targets are Sodium `mc1.21.8-0.7.3-fabric` and Iris
`1.9.6+1.21.8-fabric`.

Sodium support uses optional mixins, strict `0.7.3` source anchors, and framebuffer checks. Source
anchor and framebuffer failures log their reasons, but optional mixin target or signature drift can
leave the adapter inactive without a log message. Another Sodium version is not supported just
because the mixins happen to load.

Iris access is guarded by mod presence and runtime capability rather than a mod-version range.
Reflective access to Iris internals can fail safely as Iris changes. With a pack active, Iris owns its
programs and `colortex` layout, so Lumos does not attach material outputs to the pack's MRT. It borrows
the pack depth, moves the scene through the main target, performs self-contained nearby re-renders,
and moves the result back. If required depth or framebuffer access is unavailable, Lumos skips the
frame rather than corrupting it.
:::

::: details Shadow maps, caches, and entity shadows
Spots and gobos use one `1024x1024` map. Point lights use six `512x512` faces. Defaults allow 15
resident spot/gobo maps, 15 resident point cubemaps, and at most five new maps of each category per
frame. A light beyond either budget still illuminates without a real map until one is available.

Static maps are cached by `Light` identity. A block change, relevant chunk load or unload, resource
reload, light replacement, or moving attachment invalidates affected caches. Filtering uses
PCSS-style softening, and translucent model casters can tint transmitted light.

For each mapped light, up to 16 nearby living entities are rendered into `512x512` per-frame entity
depth maps. A light without a real map can approximate up to eight nearby living entities as capsule
blobs. An attached light omits its anchor entity only from the mapped entity-shadow path; the mapless
capsule path does not currently exclude it.
:::

::: details Runtime validation limits
Unit tests cover factory validation, light codec and saved-state round trips, malformed decoded-value
checks through `isWellFormed()`, and vanilla core-shader patch anchors. They do not execute the OpenGL
pipeline, Sodium adapter, or active-Iris behavior.

Vanilla-to-Sodium visual parity, active-Iris reduced-mode parity, arbitrary shader-pack material
ownership, shadow quality, and GL-state restoration require in-game checks on the exact runtime.
Treat dependency upgrades, shader changes, material-output wiring, and Fabulous behavior as
runtime-sensitive even when Java compilation and unit tests pass.
:::

## Next Steps

- [Workshop: Light the Lumen Probe](../workshop/lighting.md) validates one local light in game.
- [Control Light Lifetime and Persistence](./lights.md) adds attached and server-owned lifetimes.
- [Add Your First Lumos Light](./index.md) returns to the smallest mapless example.
