# Dynamic Lights (Deferred)

`glue-lumos` supplies Glue's deferred colored-light subsystem: real-time light sources with
**shape** (point / spot / gobo), **colored shadows** (stained glass tints the
light passing through it), and **cached shadow maps**. It lights the world the
player already sees — no block-light emission, no chunk relighting — by
reconstructing world positions from the scene depth buffer after
`LevelRenderer.renderLevel` returns (`RenderEvents.POST_WORLD_RENDER`).

The subsystem is registered automatically by `GlueLumosClient`; there is nothing to
initialize. Lumos runs only on the vanilla **Fancy** graphics path, reading Minecraft's main
render target directly. Opaque terrain uses a captured material buffer — a vanilla chunk replay,
or the Sodium `0.7.3` adapter when Sodium is installed. Lumos does **not** run under Fabulous
graphics or an active Iris shaderpack (Iris support will be rebuilt separately).

Package: `fr.lacaleche.glue.client.render.light`.

Add `fr.lacaleche.glue:glue-lumos` as a mod dependency. Lumos depends on `glue-render`, which owns
the frame capture, render event, shader pipeline, scene renderer, and GL-state infrastructure.

## Vanilla material buffer

Iris is not required for material-aware lighting. While Lumos lights are active,
`glue-render` replays Minecraft's already-visible opaque chunk VBOs into a private
linear material buffer. The replay uses the original section transforms, atlas,
cutout thresholds, and an `EQUAL` depth test, so it does not rescan blocks or draw
hidden surfaces. A matching depth snapshot ensures the material is used only when
opaque terrain is still the final visible surface at that pixel.

This removes the vanilla lightmap and fog from Lumos's albedo input. Vanilla bakes
biome tint, directional shade, and ambient occlusion into one vertex color, so the
fallback removes their common brightness while retaining tint ratios. The same pass
packs the terrain vertex normal into the material buffer, avoiding depth-derived
normal errors at room corners. Sodium `0.7.3` writes the same contract as a second
opaque-terrain output without replaying its geometry.

Entities and translucent surfaces use the scene-color estimate for now. Stained
glass additionally has its dedicated camera-space linear albedo buffer. Non-terrain fallback
surfaces estimate reflectance from the already-lit scene and can inherit vanilla lightmap hue
and AO. Sodium versions whose shader layout does not match the guarded `0.7.3` adapter fall
back safely to the vanilla replay. Under Fabulous graphics or an active Iris shaderpack Lumos
does not run at all.

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
tick code freely. Lights belong to the active `ClientLevel`: changing dimensions,
disconnecting, or replacing the world disposes its lights and GPU caches. Calling
`add` or `attach` while no client world exists throws `IllegalStateException`.

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
  above 1 are fine (the accumulation buffer is HDR and the composite rolls off,
  so overlapping bright lights saturate in color instead of clipping to white).
- **Range** — maximum reach in blocks. Falloff is near-inverse-square through
  most of the range and driven smoothly to zero at `range` (a light never cuts
  off mid-air). Range also sizes the shadow bake region — see performance notes.
- **Direction** — any finite, non-zero vector; normalized for you. Invalid input
  is rejected.
- **Inner / outer angles** — cone *half*-angles in degrees,
  `inner <= outer`. Full brightness inside the inner cone, smooth falloff to
  zero at the outer. They are pre-converted to cosines on the `Light`
  (`cosInner` / `cosOuter`).

## Static and attached lights

`Light` is an immutable definition. For a static light, build a new instance and
swap it when its properties change:

```java
LightManager manager = LightManager.getInstance();
manager.remove(flashlight);
flashlight = manager.add(Light.spot(eye.x, eye.y, eye.z,
        look.x, look.y, look.z, 1.0f, 0.85f, 0.6f, 3.0f, 26.0f, 12.0f, 22.0f));
```

Shadow maps and glass caches are keyed on `Light` **identity**: a static light
that keeps the same instance costs nothing after its first frame, and a replaced
instance re-bakes only its own slot.

For a moving light, attach the definition to a frame-sampled transform instead
of replacing it from a client tick:

```java
Light definition = Light.spot(0, 0, 0, 0, -1, 0,
        1.0f, 0.85f, 0.6f, 3.0f, 26.0f, 12.0f, 22.0f);

LightHandle flashlight = LightManager.getInstance().attach(
        definition, LightAttachments.entityEyes(player));

// Change color/range/cone while retaining the attachment:
flashlight.light(rebuiltDefinition);

// Remove it explicitly when its owning gameplay object is done:
flashlight.remove();
```

Built-in sources are `LightAttachments.entity(entity)`,
`LightAttachments.entityEyes(entity)`, and `LightAttachments.block(pos)`.
Entity sources interpolate position and view direction with the render partial
tick. A custom `LightAttachment` writes into the supplied `LightTransform` and
returns `false` while it cannot provide a light:

```java
LightAttachment attachment = (level, partialTick, result) -> {
    if (!active) return false;
    result.position(x, y, z).direction(dx, dy, dz);
    return true;
};
```

The handle is stable, but moving a shadow-casting light still invalidates and
re-bakes its map. Prefer `withShadow(false)` for numerous fast-moving lights.

Block changes are handled for you: `LightRenderer.onBlockChanged` re-bakes any
light whose range can see the changed block, so shadows never go stale.

## Shadows

By default every light casts real shadow maps — one for a spot/gobo, six (a
cube) for a point light — filtered with PCSS, so contact shadows are sharp and
they soften with distance from the caster.

- **Per-light opt-out:** `Light.point(...).withShadow(false)` spawns a light
  that never claims a shadow slot — no bake, no per-frame shadow sampling.
  This is the cheap way to scatter many small lights.
- **Shadow budget:** at most **6 spot/gobo** and **4 point** lights have shadow
  maps at a time; further shadow-casting lights still illuminate, just without
  shadows. The budget is configurable:

  ```java
  LightRenderer.setShadowBudget(6, 4);   // spots/gobos, points
  ```

  The recurring cost is what the budget guards: every shadowed spot is a
  screen-bounded PCSS pass per frame, and a shadowed point light is **six**.
  Shrinking the budget frees the surplus GPU textures immediately.
- **Update budget:** resident maps and new map renders are controlled separately.
  By default Lumos renders at most two new spot maps and one new point cubemap per
  frame, preserving already-cached maps and spreading bursts across frames:

  ```java
  LightRenderer.setShadowUpdateBudget(2, 1);
  ```
- **Colored shadows:** translucent casters (stained glass, ice) don't block
  light — they tint it. The tint is one flat color per pane (the sprite's
  average), stacked panes multiply (red behind blue projects violet), and the
  colored pool diffuses the further it falls behind the glass. Opaque blocks
  behind glass still shadow normally.
- Entities do not cast shadows (yet); only blocks are rasterized into the maps.

## Culling

Lights outside the view frustum, or farther from the camera than the render
distance, are skipped entirely each frame: no accumulation draw and no shadow
slot claimed. Point lights use their full sphere of influence. Spot and gobo
lights use a tighter bounding sphere around their cone, avoiding work when the
light's range sphere is visible but its directed beam is not. The bounds remain
conservative, so a light still renders whenever anything it can reach is visible.
Culled lights keep their baked maps as long as no other light needs the slot,
so looking away and back re-bakes nothing unless the pool actually ran out.

```java
LightRenderer.setMaxLightDistance(96.0);  // blocks; <= 0 = follow render distance (default)
```

Accumulation is clipped to each light's screen-space bounds with a GL scissor,
so small visible lights do not shade unrelated pixels.

## Emissive surfaces

`glue-render` provides `EmissiveMaterial` for caller-rendered entity, item, and
block-entity geometry. It supplies a fullbright render type and packed light;
you still emit the model or vertices through the returned render type:

```java
EmissiveMaterial material = EmissiveMaterial.unshaded(GLOW_TEXTURE);
VertexConsumer vertices = buffers.getBuffer(material.renderType());
renderGlow(vertices, material.packedLight());
```

Use `EmissiveMaterial.shaded(texture)` to retain Minecraft's directional entity
shading or `unshaded(texture)` for fully self-lit output. This does not modify
static block-model lighting and does not illuminate nearby surfaces by itself.

When the same object should also emit world light, `EmissiveEmitter` pairs the
material with an attached Lumos light and removes the light through `close()`:

```java
EmissiveEmitter emitter = EmissiveEmitter.attach(
        material, lightDefinition, LightAttachments.block(pos));

// When the rendered object is removed:
emitter.close();
```

## Debug tooling

The **showcase** module (not shipped with Glue itself) binds **F12** to a light
inspector: it lists active lights with in-world wireframes (reach sphere for
points, cone for spots), and lets you live-edit color / intensity / range /
shadow on–off / position / yaw–pitch / cone angles, add and delete lights. If you are tuning
light parameters, do it there and copy the numbers into your code. See
`glue-showcase/src/main/java/.../render/LightDebugHud.java`.

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
`assets/glue/shaders/internal/light/deferred.fsh` —

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
3. **`internal/gl/GlDeferredLightPass.render`** — upload the new parameters
   as uniforms next to `SpotDir` / `CosInner` / `CosOuter`, and declare them in
   the deferred shader.
4. **`internal/light/deferred.fsh`** — branch on your `LightType` value and compute
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

- Cost per frame = budgeted shadow re-bakes (only for new/moved/invalidated lights)
  + one screen-bounded accumulation pass per **visible** light (six for shadowed
  point lights, one per cube face) + one composite. Range affects bake cost (bigger
  region to rasterize), not accumulation cost.
- On the vanilla chunk renderer, active lights add one opaque-terrain material
  replay and one depth copy per frame. The replay uses the existing visible VBO
  lists and early `EQUAL` depth rejection; no chunk rebuild or CPU block walk occurs.
- Range has no API cap. Shadow and translucent-caster collection traverses loaded,
  non-empty chunk sections intersecting the light instead of scanning a fixed
  dense cube. Point-light casters are collected once and reused for all six faces.
  Extremely large values still increase the number of loaded sections considered;
  visibility, resident-shadow, and update budgets remain the practical controls.
- Lumos adds its result on top of the vanilla scene, so sky and block light remain
  the primary ambient fill. Each dynamic light also contributes a small, broad,
  desaturated fill proportional to its intensity. This approximates first-bounce
  light and limits excessive contrast at corners without attempting full global
  illumination.
- The deferred pass shades the **frontmost** surface in the depth buffer.
  Vanilla glass writes depth, so glass in front of a lit floor receives the
  light as a glow on the pane (by design), but surfaces *behind* other
  translucents can't be lit independently.
- **Fabulous graphics** disables Lumos entirely: translucents composite from a
  separate target, so the captured material depth would not match the main scene
  depth. Switch to Fancy graphics for dynamic lighting.
- Opaque Vanilla and supported Sodium terrain use captured geometric normals.
  Entities and translucent surfaces reconstruct normals from depth, so extremely
  thin geometry (flowers, tripwire) lights approximately.
- **Active Iris shaderpacks** disable Lumos entirely; the pack owns its own colortex
  layout. Iris support will be rebuilt separately. Iris installed with shaders
  disabled runs the normal vanilla Fancy path with full material capture.
