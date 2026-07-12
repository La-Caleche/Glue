# Dynamic Lights (Deferred)

Glue ships a deferred colored-light subsystem: real-time light sources with
**shape** (point / spot / gobo), **colored shadows** (stained glass tints the
light passing through it), and **cached shadow maps**. It lights the world the
player already sees — no block-light emission, no chunk relighting — by
reconstructing world positions from the scene depth buffer after
`LevelRenderer.renderLevel` returns (`RenderEvents.POST_WORLD_RENDER`).

The subsystem is registered automatically by `GlueClient`; there is nothing to
initialize. It currently targets the **vanilla render path** (it runs under
Iris, but parity with shaderpacks is still in progress — see
[Iris / Oculus Compatibility](iris-compat.md)).

Package: `fr.lacaleche.glue.client.render.light`.

## Quick start

```java
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightManager;

// A warm point light hovering at (x, y, z), reaching 12 blocks.
Light lamp = LightManager.getInstance().add(
        Light.point(x, y, z, 1.0f, 0.85f, 0.6f, /*intensity*/ 2.5f, /*range*/ 12.0f));

// Later:
LightManager.getInstance().remove(lamp);
```

`add` returns the same instance for convenient field storage. `LightManager` is
synchronized and snapshotted once per frame, so you may add/remove from client
tick code freely.

## Light types

| Type | Factory | Shape |
|---|---|---|
| `POINT` | `Light.point(x, y, z, r, g, b, intensity, range)` | omnidirectional sphere |
| `SPOT` | `Light.spot(x, y, z, dirX, dirY, dirZ, r, g, b, intensity, range, innerDeg, outerDeg)` | cone |
| `GOBO` | `Light.gobo(x, y, z, dirX, dirY, dirZ, r, g, b, intensity, range, innerDeg, outerDeg, goboTextureId)` | cone masked by a projected texture |

### Parameters

- **Position** — absolute world coordinates (`double`). The renderer converts to
  camera-relative space internally, so far-from-origin precision is handled.
- **Color** — *linear* RGB in `[0, 1]`. `intensity` multiplies it; values well
  above 1 are fine (the accumulation buffer is HDR and the composite tonemaps,
  so overlapping bright lights saturate in color instead of clipping to white).
- **Range** — maximum reach in blocks. Falloff is near-inverse-square through
  most of the range and driven smoothly to zero at `range` (a light never cuts
  off mid-air). Range also sizes the shadow bake region — see performance notes.
- **Direction** — any non-zero vector; normalized for you. Degenerate input
  falls back to straight down.
- **Inner / outer angles** — cone *half*-angles in degrees,
  `inner <= outer`. Full brightness inside the inner cone, smooth falloff to
  zero at the outer. They are pre-converted to cosines on the `Light`
  (`cosInner` / `cosOuter`).

## Lights are immutable — move them by replacing them

`Light` has no setters. To move, recolor, or otherwise animate a light, build a
new instance and swap it:

```java
LightManager manager = LightManager.getInstance();
manager.remove(flashlight);
flashlight = manager.add(Light.spot(eye.x, eye.y, eye.z,
        look.x, look.y, look.z, 1.0f, 0.85f, 0.6f, 3.0f, 26.0f, 12.0f, 22.0f));
```

This is not a workaround; it is the invalidation mechanism. Shadow maps and
glass caches are keyed on `Light` **identity**: a light that keeps the same
instance costs *nothing* after its first frame (its maps are cached), and a
replaced instance re-bakes only its own slot. The corollary:

- **Static lights are cheap.** Bake once, then free.
- **A light you replace every frame re-bakes every frame** (a spot is one
  1024² depth pass over nearby blocks; a point light is six 512² passes). A
  handful of moving lights is fine; don't attach one to every particle.

Block changes are handled for you: `LightRenderer.onBlockChanged` re-bakes any
light whose range can see the changed block, so shadows never go stale.

## Shadows

Every light casts real shadow maps — one for a spot/gobo, six (a cube) for a
point light — filtered with PCSS, so contact shadows are sharp and they soften
with distance from the caster.

- At most **3 spot/gobo** and **3 point** lights have shadow maps at a time
  (`ShadowBaker` caps). Further lights still illuminate, just without shadows.
- **Colored shadows:** translucent casters (stained glass, ice) don't block
  light — they tint it. The tint is one flat color per pane (the sprite's
  average), stacked panes multiply (red behind blue projects violet), and the
  colored pool diffuses the further it falls behind the glass. Opaque blocks
  behind glass still shadow normally.
- Entities do not cast shadows (yet); only blocks are rasterized into the maps.

## Debug tooling

The **testmod** (not shipped with Glue itself) binds **F12** to a light
inspector: it lists active lights with in-world wireframes (reach sphere for
points, cone for spots), and lets you live-edit color / intensity / range /
position / yaw–pitch / cone angles, add and delete lights. If you are tuning
light parameters, do it there and copy the numbers into your code. See
`src/testmod/.../render/LightDebugHud.java`.

## Creating new shapes

### The supported way: gobo masks

A **gobo** (from stage lighting: *goes-between*) is a texture projected by the
light; its **red channel** masks the cone. This gets you arbitrary projected
shapes — window frames, foliage dapple, a logo, a cathedral rose — with no
engine changes:

```java
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

ResourceLocation mask = ResourceLocation.fromNamespaceAndPath("mymod", "textures/gobo/rose.png");

// AbstractTexture.getTexture() is the GpuTexture; on the GL backend it is a
// GlTexture carrying the raw id the light renderer binds.
int goboId = ((GlTexture) Minecraft.getInstance().getTextureManager()
        .getTexture(mask).getTexture()).glId();

Light rose = LightManager.getInstance().add(Light.gobo(
        x, y, z, 0f, -1f, 0f,          // aimed straight down
        1.0f, 0.2f, 0.3f, 3.0f, 20.0f,
        30.0f, 40.0f,                   // the cone still bounds the projection
        goboId));
```

Notes:

- The texture must be **loaded** (registered with the `TextureManager`) before
  you resolve the id; resolving it lazily on first use is the safe pattern.
  The id is a *live GL texture id* — if the texture is ever closed/reloaded,
  rebuild the light with the new id.
- White (red = 1) is fully lit, black is dark; grays give partial intensity.
  Keep a black border on the mask, or the cone's edge will clip the pattern
  with a hard line.
- The mask is projected through the light's view like a slide projector: it
  scales with the cone angle and stays glued to the world as the light moves.

### A genuinely new shape (engine work)

If a masked cone can't express what you need (a fluorescent *tube*, an *area*
panel, a *ring*), you are adding a light type to the engine. The shape of a
light lives in **one place**: the `shape` factor in
`assets/glue/shaders/internal/glue_light_deferred.fsh` —

```glsl
float shape = 1.0;
if (LightType >= 1) {
    float cosA = dot(-L, normalize(SpotDir));
    shape = smoothstep(CosOuter, CosInner, cosA);   // <- this IS the spot cone
    ...
}
```

`shape` is a `0..1` multiplier over the base distance falloff, computed from
the fragment's position relative to the light. A new shape is a new way of
computing it. The full checklist:

1. **`LightType`** — add the enum constant.
2. **`Light`** — add a static factory carrying whatever parameters the shape
   needs (an axis and a half-length for a tube, a width/height for a panel).
   Keep the class immutable; parameters are final fields like `cosInner`.
3. **`internal/GlLightRenderer.accumulateLight`** — upload the new parameters
   as uniforms next to `SpotDir` / `CosInner` / `CosOuter`, and declare them in
   the deferred shader.
4. **`glue_light_deferred.fsh`** — branch on your `LightType` value and compute
   `shape`. For *area-like* lights you typically also replace `L` (the
   to-light direction) with the direction to the **nearest point** on the
   light's surface, so `N·L` and the falloff behave across the light's extent.
5. **Shadows** — decide what a shadow map means for your shape.
   `ShadowBaker` treats every non-`POINT` light as "one map along
   `light.direction`" (`bakeSpot`), which is usually fine for anything
   directional; a shape that wraps around (tube, ring) either reuses the
   six-face point path or caps `castsShadow` off. The map itself is just
   "depth from the light's position" — it does not need to know the shape.

Things you do **not** need to touch: position reconstruction, normals,
attenuation window, PCSS, the colored-shadow path, the HDR composite — they
are all shape-agnostic and multiply your `shape` factor in at the end.

If the shape you want is *"a cone with soft patterned edges"*, resist the
engine route — draw the pattern into a texture and use a gobo. The engine
route is for shapes whose **geometry** differs, not whose silhouette does.

## Performance & limitations

- Cost per frame = shadow re-bakes (only for new/replaced/invalidated lights)
  + one full-screen accumulation pass per light (six for shadowed point
  lights, one per cube face) + one composite. Range affects bake cost (bigger
  region to rasterize), not accumulation cost.
- The deferred pass shades the **frontmost** surface in the depth buffer.
  Vanilla glass writes depth, so glass in front of a lit floor receives the
  light as a glow on the pane (by design), but surfaces *behind* other
  translucents can't be lit independently.
- **Fabulous graphics** is unhandled: translucents render to a separate target
  there, so glass is invisible to the lighting pass.
- Normals are reconstructed from depth (the vanilla path has no G-buffer), so
  extremely thin geometry (flowers, tripwire) lights approximately.
