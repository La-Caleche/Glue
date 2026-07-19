# Sodium material adapter

**Status:** implemented for Sodium `0.7.3+mc1.21.8`; visual parity validation pending.
**Verified against:** Sodium `0.7.3+mc1.21.8` (Fabric), Glue `glue-render` / `glue-lumos`.

This document specifies the renderer adapter that lets Lumos produce a correct
image on the setup nearly every player runs. It is written to be executable by someone who
has never seen this codebase.

---

## 1. TL;DR

Lumos needs **material data**: per pixel, the *linear albedo* and *geometric normal* of the opaque
terrain surface, plus an id saying the pixel *is* terrain. On the vanilla chunk renderer it gets
this by source-patching Minecraft's own `core/terrain` shader to write extra outputs during the
terrain draw (`CoreShaderMaterialPatch`). Sodium replaces vanilla's chunk renderer wholesale, so
`core/terrain` never runs and terrain would reach Lumos with no material at all — falling back to
guessing albedo from the already-lit scene colour.

The guess is not a small error. **It was the dominant visual defect in the mod.**

The adapter in `client.render.internal.material` applies the same idea to Sodium's own chunk
shaders, writing into the **same shared G-buffer** the vanilla path fills. Both terrain paths
therefore hand consumers one identical contract.

---

## 2. The bug this fixes (measured, not asserted)

Measured when the fallback still *lit* uncaptured surfaces from an estimated albedo. It no longer
does — an uncaptured surface now takes no Lumos light at all (§11) — so these are a record of why
the adapter exists, not of what a Sodium regression looks like today. The physics below is why no
fallback was ever going to work, and that part has not changed.

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

The oracle is `CoreShaderMaterialPatch.patchTerrainFragment` (the vanilla `core/terrain` patch) in
`client.render.internal.gbuffer`. It is the reference implementation you validate against; the
Sodium patch must produce numerically the same values from Sodium's equivalent inputs.

Write **three** fragment outputs into the shared G-buffer (`GBufferTargets`), whose attachments
are already bound for you:

| output | contents |
|---|---|
| `location = 1` (`RGBA16F`) | `RGB` = **linear** albedo — block texel × biome/model tint, with vanilla's AO/face-shade coefficient *divided out*; `A` = geometric normal, octahedral-packed into 8 bits (4+4) |
| `location = 2` (`RGBA8`) | `R` = material id — **`1.0/255.0` for terrain**; `GBA` = this fragment's own `gl_FragCoord.z`, packed to 24 bits |
| `location = 3` (`RGBA8`) | `R` = roughness `1.0`, `G` = metalness `0.0`, `B` = F0 `0.04`, `A` = `1.0` (the "props present" flag). Generic rough dielectric; water and metal overwrite the pixel from their own captures. |

The AO/shade divide-out, matching the vanilla path:

```glsl
// Vanilla bakes a scalar AO/face-shade coefficient into vertex RGB. Divide out that
// common magnitude while retaining biome/model tint ratios.
float shade = max(rawColor.r, max(rawColor.g, rawColor.b));
vec3  tint  = shade > 1e-4 ? rawColor.rgb / shade : vec3(1.0);
glue_Material = vec4(srgbToLinear(texel.rgb * tint), packNormal(normalize(normal)));
```

Copy `glueSrgbToLinear`, `gluePackNormal`, `glueSignNotZero` and `gluePackDepth24` unchanged — the
consumer (`internal/light/deferred.fsh:unpackNormal` / `unpackDepth24`) depends on the exact packing.

**No separate depth snapshot.** Ownership is established by the depth each fragment packs into its
own id output: consumers reconstruct that depth and the scene depth to world space and compare with
a distance-scaled tolerance. Because the material is written in the same draw as the colour, that
depth is correct by construction — nothing has to be copied or matched afterwards.

Consumers, for reference:
- `internal/light/deferred.fsh` — `GBufferAlbedo` / `GBufferId` / `HasGBuffer` (normals + ownership)
- `internal/light/composite.fsh` — same uniforms (albedo + ownership)

---

## 4. Why Sodium needs its own adapter

The vanilla terrain capture is keyed to vanilla's chunk render path on both ends: the source patch
targets Minecraft's `core/terrain` shader, and `GBufferCapture` arms the FBO redirect on the
identity of `RenderPipelines.SOLID` / `CUTOUT_MIPPED` / `CUTOUT`. Sodium uses none of them — it
compiles its own chunk shaders and drives its own render passes — so neither half fires and terrain
would land in the G-buffer with no id.

`TerrainMaterialBuffer` therefore branches on `FabricLoader.isModLoaded("sodium")` and hands the
frame to `SodiumTerrainMaterialCapture`, which owns the attach/detach around Sodium's opaque pass.
That branch is the only terrain-renderer-specific code in the gate.

Iris trips the whole gate via `RenderCompat.isIrisShaderEnabled()` — but only when a shaderpack is
*active*. See §9.

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
AO/face-shade × biome tint. That is *precisely* the signal the vanilla patch forwards as
`glue_RawColor`. You do not need to un-multiply anything. You just need to not multiply it in the
first place.

Vanilla's `core/terrain` has the identical property, and for the identical reason — it also folds
the lightmap into `vertexColor` in the **vertex** stage. Both patches therefore forward the raw
pre-lightmap colour to the fragment stage rather than trying to recover albedo afterwards. This
symmetry is why the two paths can share one contract.

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

**Caveat, verified in-game:** the derivative normal is clean head-on but **jitters at grazing
angles** — perspective makes the finite difference a poor tangent estimate and it varies per
2×2 quad, producing a fine woven grain on angled surfaces that the vanilla vertex normal never
shows. The adapter therefore **snaps a near-axis derived normal to its exact axis** (terrain is
overwhelmingly axis-aligned; a genuinely diagonal face, max component ≤ 0.9, keeps the derived
value). That removes the grain on block faces while staying correct for the rare diagonal.

(Pass `v_WorldPos` from the vertex shader — Sodium already computes it as
`u_RegionOffset + _get_draw_translation(_draw_id) + _vert_position`.)

---

## 6. Design — two options

### Option A: MRT + shader source patch  ← **implemented**

Let Sodium draw terrain exactly once, but make its fragment shader write a *second* output
into your material texture.

1. `TerrainMaterialBuffer` routes the frame to `SodiumTerrainMaterialCapture`. It allocates
   nothing: `GBufferCapture` already owns and cleared the material textures for this frame.
2. A pseudo mixin injects after `ShaderChunkRenderer.begin`, after Sodium has bound its
   framebuffer. For every non-translucent pass it attaches the **shared G-buffer's** three
   material textures as `GL_COLOR_ATTACHMENT1..3` on *Sodium's own* framebuffer and selects all
   four draw buffers. It first checks that framebuffer is Minecraft's main target and that
   attachment 1 is free, and bails out loudly if either is false.
3. A second pseudo mixin patches `ShaderLoader.getShaderSource` for
   `sodium:blocks/block_layer_opaque.{vsh,fsh}`, returning patched source that adds:
   - **vsh:** `out vec4 glue_RawColor;` (= `_vert_color`, *unlit*) and `out vec3 glue_WorldPos;`
   - **fsh:** the three `layout(location = 1..3)` outputs computing the §3 contract
4. Before `ShaderChunkRenderer.end`, restore the previous draw-buffer state and detach
   attachments 1–3. Solid and cutout passes accumulate into the same shared attachments. No depth
   copy is needed — the writes already targeted the main depth attachment.

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

**The depth tolerance — now historical, and worth knowing why.** An earlier design captured
material into a *private* target and gated consumers on `abs(materialDepth - sceneDepth) < 1e-5`
against a copied depth snapshot. That tolerance was a trap: at `1e-7` (under two ULP of a 24-bit
buffer) the copy's quantisation made the match flicker on and off per pixel, so the normal kept
switching between the clean packed value and the noisy depth-reconstructed fallback and read as
grain. Widening it only traded one failure for another — no window can both tolerate a copy and
separate a pane from a mob behind it. **Do not reintroduce depth-matching.** Ownership is now the
world-space test of §3 against the depth the fragment itself packed, which needs no tolerance
tuning because nothing is copied.

**Clear once.** The material attachments are cleared once at frame start by `GBufferCapture`, not
once per layer. Otherwise cutout passes erase solid terrain; without any clear, stale material can
occasionally survive when old and new geometry have the same depth.

**Alpha cutout.** The opaque pass includes cutout layers. Sodium gates discard on
`USE_FRAGMENT_DISCARD` / `_material_alpha_cutoff(v_Material)`. Your material output must
discard on exactly the same condition, or leaves and grass write albedo into pixels that are
transparent in the colour buffer.

**Fog.** Sodium's fsh applies `_linearFog(...)` to `fragColor`. Your `fragMaterial` must **not**
be fogged — albedo is a surface property. Write it before/independently of the fog call.

**Resize.** The adapter allocates nothing. `GBufferTargets` follows the main render target's size
and re-points its borrowed colour/depth every frame; the adapter just reads the current texture ids
and bails out if any is unavailable (`<= 0`).

**Restore what you attached.** The adapter attaches to a framebuffer it does not own, so every exit
path — including the one where the bound framebuffer changed mid-pass — must restore the previous
draw-buffer set and detach attachments 1–3. `SodiumTerrainMaterialCapture` also force-restores at
the start of the next frame if a pass ended without doing so, then disables itself.

**Translucents.** Only the *opaque* passes feed terrain material here. Glass, water and metal are
separate material classes captured by their own post-world re-renders (`GlassSceneRenderer`,
`WaterSceneRenderer`, `MetalSceneRenderer`) into the same shared attachments. Don't touch them.

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
pack-defined `colortex` layout — attachments 1–3 are the pack's, not ours. Lumos does not run at all
under an active Iris pack (or under Fabulous graphics), and that is a structural boundary rather
than pending work; see [Dynamic Lights](lights.md#performance--limitations) for the reasoning. The
Sodium adapter therefore covers the vanilla/Sodium **Fancy** path — the setup nearly every player
runs.

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

> **Keep the vanilla path intact** — today that is `CoreShaderMaterialPatch`'s `core/terrain`
> patch, not a separate replay. It remains the oracle you validate the adapter against (§11), and
> it is what runs for every player without Sodium. The two paths must keep writing byte-identical
> values into the shared G-buffer; if you change one contract, change both.
>
> *(History: the vanilla path was originally a private second-draw replay of the chunk VBOs —
> `VanillaTerrainMaterialCapture`, `ChunkSectionsToRenderMixin`, `terrain_material.{vsh,fsh}`.
> Those are gone. The replay was Option B (§6) for vanilla: a second geometry submission whose
> material lived in a separate target and had to be depth-matched back to the scene. Moving vanilla
> terrain onto the same source-patch + MRT seam as Sodium removed that draw and the depth-matching
> with it. The warning above survives the rewrite: whichever path is the reference, do not delete
> it while reimplementing it.)*

---

## 11. Validation protocol

The adapter logs `Sodium 0.7.3 terrain material adapter active (routed to shared G-buffer)` after
its first successful opaque pass. This confirms capture, not image parity; the measurements below
remain required.

**The vanilla path is ground truth.** Run the same scene with and without Sodium; the two
images must converge.

The showcase's development runtime is selected by two Gradle properties (see
`glue-showcase/build.gradle.kts`; defaults live in the root `gradle.properties`):

| property | effect |
|---|---|
| `glue.showcase.sodium=false` | omit Sodium `0.7.3` — the vanilla reference run |
| `glue.showcase.iris=true` | add Iris at runtime; **also forces Sodium in**, since Iris hard-depends on it |

Pass them on the command line to override, e.g. `-Pglue.showcase.sodium=false`. Check the current
default in `gradle.properties` before assuming which run you are getting, and ensure no manually
installed Sodium jar remains in the selected run directory.

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

A useful failure signature: if terrain never lands in the G-buffer, the picture does not look
obviously broken — with the uncaptured-surface cap at 0, terrain simply receives **no Lumos light**
and keeps its vanilla look. Do not trust your eyes for that one. Check it directly: the FBO debug
HUD (**F8**) exposes `GBuffer MaterialID`, where captured terrain reads as a near-black red channel
(id `1/255`) and unclaimed pixels as exact zero.
