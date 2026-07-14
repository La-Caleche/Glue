# Sodium material adapter

**Status:** implemented for Sodium `0.7.3+mc1.21.8`; visual parity validation pending.
**Verified against:** Sodium `0.7.3+mc1.21.8` (Fabric), Glue `glue-render` / `glue-lumos`.

This document specifies the renderer adapter that lets Lumos produce a correct
image on the setup nearly every player runs. It is written to be executable by someone who
has never seen this codebase.

---

## 1. TL;DR

Lumos needs a **material buffer**: a screen-sized texture holding, per pixel, the *linear
albedo* and *geometric normal* of the opaque terrain surface. `glue-render` builds one by
replaying Minecraft's opaque chunk VBOs (`TerrainMaterialBuffer`). Sodium replaces vanilla's
chunk renderer wholesale, so that replay never runs, and Lumos silently falls back to
guessing albedo from the already-lit scene colour.

The guess is not a small error. **It is the dominant visual defect in the mod.**

The adapter in `client.render.internal.material` produces the same material-buffer contract
when Sodium is the terrain renderer.

---

## 2. The bug this fixes (measured, not asserted)

Test scene: a sealed white-concrete room, one white `POINT` light, identical camera.

| probe | with Sodium (fallback) | without Sodium (material buffer) | truth |
|---|---|---|---|
| wall B/R ratio | 0.963 | **1.048** | 1.048 — white concrete is a slightly *cool* white |
| wood plank saturation | 0.16 | **0.55** | 0.55 |
| room corners | hard facets | smooth | smooth |

Two independent failures, one root cause:

**Albedo.** With no material buffer, the composite estimates reflectance from the lit scene
colour. But `sceneColour = albedo × light` — one equation, two unknowns. The estimate is a
scalar multiple of the scene, so it **inherits the vanilla lightmap's hue**. A *white* lamp
therefore renders **blue** in a room with a skylight (measured B/R 1.44) and **yellow** in a
sealed room (measured B/R 0.93). No fallback can fix this; it is ill-posed by construction.

**Normals.** With no material buffer, normals are reconstructed from the depth buffer. At a
room corner the depth gradient straddles two perpendicular faces, so the reconstructed normal
is a ~45° average of both. That is the corner faceting.

---

## 3. The contract — what you must produce

Match `glue-render/src/main/resources/assets/glue/shaders/internal/material/terrain_material.fsh`
exactly.
It is the reference implementation and the oracle you validate against.

A screen-sized `RGBA8` colour target, plus a depth snapshot:

| channel | contents |
|---|---|
| `RGB` | **linear** albedo — block texel × biome/model tint, with vanilla's AO/face-shade coefficient *divided out* |
| `A` | geometric normal, octahedral-packed into 8 bits (4+4) |

The AO/shade divide-out, verbatim from the vanilla path:

```glsl
// Vanilla bakes a scalar AO/face-shade coefficient into vertex RGB. Divide out that
// common magnitude while retaining biome/model tint ratios.
float shade = max(vertexColor.r, max(vertexColor.g, vertexColor.b));
vec3  tint  = shade > 1e-4 ? vertexColor.rgb / shade : vec3(1.0);
fragColor = vec4(srgbToLinear(texel.rgb * tint), packNormal(normalize(vertexNormal)));
```

Copy `srgbToLinear`, `packNormal` and `signNotZero` from that file unchanged — the consumer
(`internal/light/deferred.fsh:unpackNormal`) depends on the exact packing.

Also required: a **depth snapshot** taken at capture time. The consumers gate on
`abs(materialDepth - sceneDepth) < 1e-7` to confirm opaque terrain is still the frontmost
surface at that pixel. See §7 — this tolerance is a trap.

Consumers, for reference:
- `internal/light/deferred.fsh` — `MaterialAlbedo` / `MaterialDepth` / `HasMaterial` (normals)
- `internal/light/composite.fsh` — same uniforms (albedo)

---

## 4. Why Sodium breaks it today

Before the adapter, `glue-render/.../TerrainMaterialBuffer.java` stopped here:

```java
if (HAS_SODIUM) return;
```

The capture hooks `ChunkSectionsToRender.renderGroup` (`ChunkSectionsToRenderMixin`), which
is vanilla's chunk render path. Sodium does not use it, so with Sodium installed there are no
vanilla chunk VBOs to replay at all. The guard is honest, not lazy.

Iris trips the same bail-out via `RenderCompat.isIrisShaderEnabled()` — but only when a
shaderpack is *active*. See §9.

---

## 5. Sodium internals reference

All signatures below were read from the remapped `0.7.3+mc1.21.8` jar with `javap`. Sodium
exposes **no public API** for terrain rendering (`net.caffeinemc.mods.sodium.api.*` covers
block entities, colour utils and vertex writers only). Everything here is internal and may
move between Sodium releases — see §10.

### The pieces that matter

```java
// net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader
public static String getShaderSource(ResourceLocation location);   // <-- THE PATCH POINT

// net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass
public RenderTarget  getTarget();          // which framebuffer terrain draws into
public boolean       isTranslucent();
public boolean       supportsFragmentDiscard();

// net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer
public void render(ChunkRenderMatrices, CommandList, ChunkRenderListIterable,
                   TerrainRenderPass, CameraTransform, FogParameters, boolean);
```

### Sodium's chunk shaders — read these first

`assets/sodium/shaders/blocks/block_layer_opaque.vsh` and `.fsh`, inside the Sodium jar.

**The single most important fact in this document:**

```glsl
// block_layer_opaque.vsh
v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);
//        ^^^^^^^^^^^   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//        raw vertex    the vanilla lightmap, applied HERE in the
//        colour        VERTEX shader
```

`_vert_color` (from the `a_Color` attribute) is the **raw vertex colour before the lightmap** —
AO/face-shade × biome tint. That is *precisely* the `vertexColor` input the vanilla
`terrain_material.fsh` consumes. You do not need to un-multiply anything. You just need to not
multiply it in the first place.

### Vertex format — there is no normal

`CompactChunkVertex` (`render/chunk/vertex/format/impl/`) declares:

```glsl
in uvec2 a_Position;      // 20 bits per axis, interleaved
in vec4  a_Color;         // <-- raw AO/shade x tint. What you want.
in uvec2 a_TexCoord;
in uvec4 a_LightAndData;  // .xy lightmap coord, .z material bits, .w draw id
```

**No normal attribute.** Do not try to add one, and do not try to recover the facing from
Sodium's per-face model-part split (`MODEL_POS_X` … `MODEL_NEG_Z` in `DefaultChunkRenderer`).

Instead — and this is the trick that makes the whole thing easy — **derive the normal in the
fragment shader from screen-space derivatives of the interpolated world position**:

```glsl
vec3 N = normalize(cross(dFdx(v_WorldPos), dFdy(v_WorldPos)));
```

This is **not** the same as the depth-buffer reconstruction that causes the corner faceting.
Derivatives are evaluated *within a single triangle*, so they can never straddle two surfaces.
The result is the exact flat geometric normal, for free, with no vertex attribute.

(Pass `v_WorldPos` from the vertex shader — Sodium already computes it as
`u_RegionOffset + _get_draw_translation(_draw_id) + _vert_position`.)

---

## 6. Design — two options

### Option A: MRT + shader source patch  ← **implemented**

Let Sodium draw terrain exactly once, but make its fragment shader write a *second* output
into your material texture.

1. `TerrainMaterialBuffer` selects `SodiumTerrainMaterialCapture`, which resizes and clears its
   own material target once.
2. A pseudo mixin injects after `ShaderChunkRenderer.begin`, after Sodium has bound its
   framebuffer. For every non-translucent pass it attaches the material texture as
   `GL_COLOR_ATTACHMENT1` and selects both draw buffers.
3. A second pseudo mixin patches `ShaderLoader.getShaderSource` for
   `sodium:blocks/block_layer_opaque.{vsh,fsh}`, return patched source that adds:
   - **vsh:** `out vec4 v_RawColor;` (= `_vert_color`, *unlit*) and `out vec3 v_WorldPos;`
   - **fsh:** `layout(location = 1) out vec4 fragMaterial;` computing the §3 contract
4. Before `ShaderChunkRenderer.end`, restore the previous draw-buffer state and detach
   attachment 1. Copy the main target's opaque depth texture into the material target and
   mark the capture available. Solid and cutout passes accumulate into the same color target.

**Pros:** no additional geometry submission. No need to replicate Sodium's uniform setup,
vertex-format binding, or draw-command construction — that stays Sodium's problem. This is the
approach Iris uses to inject its own G-buffer outputs, so it is known-workable.

**Cons:** the patch is textual and breaks if Sodium renames `_vert_color` / restructures the
shader. Mitigate with a strict guard: if the expected anchor string is absent, log once and
disable the adapter (fall back), never crash.

### Option B: second geometry pass with your own program

Mixin `DefaultChunkRenderer.render(...)` at `RETURN`, bind your FBO and your own `GlProgram`,
and re-issue the draw.

**Pros:** no shader-source string matching.
**Cons:** you must replicate Sodium's uniform plumbing (`ChunkShaderInterface`: projection,
model-view, region offset, `u_BlockTex`, `u_TexCoordShrink`), bind its compressed vertex
format yourself, and pay a full second terrain draw every frame. Strictly more coupling *and*
strictly more cost.

**Take Option A.** Option B is documented only so the next person doesn't rediscover it and
assume it was overlooked.

---

## 7. Pitfalls that will cost you a day each

**The depth tolerance.** Consumers gate on `abs(materialDepth - sceneDepth) < 1e-7`. A 24-bit
depth buffer has a quantum of ~6e-8, so that tolerance is under two ULP. If your material
depth comes from a *different* framebuffer than the scene depth, or is blitted/copied with any
format conversion, this test fails on **every pixel** and `terrainMaterial` is silently false
everywhere — you will get the exact fallback behaviour you were trying to fix, with no error
message. The MRT writes against the main depth attachment, then copies that exact opaque depth
texture into the material target after each pass. No depth re-render or format conversion occurs.

**Clear once.** The material color target is cleared once at frame start, not once per layer.
Otherwise cutout passes erase solid terrain; without any clear, stale material can occasionally
survive when old and new geometry have the same depth.

**Alpha cutout.** The opaque pass includes cutout layers. Sodium gates discard on
`USE_FRAGMENT_DISCARD` / `_material_alpha_cutoff(v_Material)`. Your material output must
discard on exactly the same condition, or leaves and grass write albedo into pixels that are
transparent in the colour buffer.

**Fog.** Sodium's fsh applies `_linearFog(...)` to `fragColor`. Your `fragMaterial` must **not**
be fogged — albedo is a surface property. Write it before/independently of the fog call.

**Resize.** Each implementation owns a `MaterialCaptureTarget` that follows the main render
target's size.

**Frame publication.** Each implementation publishes `Optional<MaterialFrame>` only for its
matching frame sequence. A failed or partial pass publishes nothing.

**Translucents.** Only the *opaque* passes feed this buffer. Stained glass has its own
camera-space albedo buffer (`GlassSceneRenderer`). Don't touch it.

---

## 8. Exposure baseline

`GlLightCompositePass.EXPOSURE` is provisionally `1.0f`. It was `6.0f`, compensating for the
fallback's unusually dark albedo estimate. Keep unity while validating adapter parity; calibrate
exposure and default light intensities only after Sodium and vanilla produce matching material
inputs. Do not use exposure to hide a parity failure.

---

## 9. Iris

`RenderCompat.isIrisShaderEnabled()` trips the same bail-out, but only when a shaderpack is
**active**. With Iris installed and no pack loaded (the common case), Iris is dormant and the
Sodium adapter is what runs — so fixing Sodium fixes the Iris-installed-but-idle case for free.

When a pack *is* active, the adapter does not attach its MRT because the pack owns an arbitrary,
pack-defined `colortex` layout. There is intentionally no speculative second terrain pass:
material color alone does not prove alignment with the shaderpack's scene depth, projection, or
composite destination. Lumos still runs under an active pack through the `glue:iris_final_color`
world pipeline — but with **no** material buffer, so it falls back to the scene-color albedo
estimate everywhere (with the coherence caveats documented for that provider). See
[World Render Pipelines](world-render-pipelines.md).

---

## 10. Version fragility

You are coupling to Sodium internals; that is unavoidable, since Sodium ships no API for this.
Contain the blast radius:

- Keep every touchpoint optional. `SodiumShaderLoaderMixin` is a string-target `@Pseudo` hook;
  the typed renderer hook is rejected by `GlueRenderMixinPlugin` when Sodium is absent.
  `TerrainRenderPass.isTranslucent()` is accessed reflectively inside the capture implementation.
- Guard the shader patch on an anchor string. If it doesn't match, log once, disable the
  adapter, fall back. Never crash, never render garbage.
- Pin the tested Sodium version in this document when you land it.

> **Do not delete the vanilla path** (`VanillaTerrainMaterialCapture`,
> `ChunkSectionsToRenderMixin`, `terrain_material.{vsh,fsh}`). It is the reference
> implementation currently known to produce a correct image, and it is the oracle you validate
> the adapter against (§11). Deleting your reference implementation while reimplementing it is
> the worst available move.

---

## 11. Validation protocol

The adapter logs `Sodium 0.7.3 terrain material adapter active` after its first successful
opaque pass. This confirms capture, not image parity; the measurements below remain required.

**The vanilla path is ground truth.** Run the same scene with and without Sodium; the two
images must converge.

The showcase includes Sodium `0.7.3` at development runtime by default. Launch with
`-Pglue.showcase.sodium=false` for the vanilla reference run (and ensure no manually installed
Sodium jar remains in the selected run directory).

Scene: sealed room, white concrete walls, wood plank floor, one white `POINT` light, fixed
camera. Screenshot both. Sample an 11×11 average on each flat patch.

| probe | fallback (broken) | **target — must match the no-Sodium reference** |
|---|---|---|
| wall B/R | 0.963 | **1.048** |
| wood plank saturation | 0.16 | **0.55** |
| wall sRGB | ~178 | **~190** |
| corners | hard facets | smooth gradient |

Convergence on **all four** means the adapter is correct. Wall brightness alone is not
sufficient — it was the one number the broken fallback got roughly right.

A useful failure signature: if `HasMaterial` is never 1, the picture will look *exactly* like
the current Sodium fallback rather than obviously broken. Assert the flag directly; do not
trust your eyes for that one.
