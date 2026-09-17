---
title: Rendering Pipelines
description: Build a complete reloadable core-shader pipeline and draw the pedestal display through it safely.
artifact: glue-render
modId: glue-render
environment: client
---

# Rendering Pipelines

This task gives the Light Workshop pedestal display a warm orange core shader. Use `GluePipeline`
when an entity, block, or particle renderer already submits textured vertices through a
`MultiBufferSource`, but those vertices need a different Minecraft `RenderPipeline`.

## Build the Smallest Complete Pipeline

The complete path has one Glue definition, two core shader stages, a reload-safe resolver, and the
draw call:

```text
assets/lightworkshop/glue/pipelines/pedestal_glow.json
assets/lightworkshop/shaders/core/entity.vsh
assets/lightworkshop/shaders/core/pedestal_glow.fsh
```

### 1. Define the pipeline

The file name creates the ID `lightworkshop:pedestal_glow`:

```json [src/main/resources/assets/lightworkshop/glue/pipelines/pedestal_glow.json]
{
  "vertex_shader": "lightworkshop:core/entity",
  "fragment_shader": "lightworkshop:core/pedestal_glow",
  "blend": "translucent",
  "alpha_cutout": 0.1,
  "cull": false,
  "iris_program": "ENTITIES_TRANSLUCENT",
  "category": "entity"
}
```

### 2. Add the entity vertex shader

This source matches the entity preset's `NEW_ENTITY` format, matrix/light snippet, and three
samplers:

```glsl [src/main/resources/assets/lightworkshop/shaders/core/entity.vsh]
#version 150

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
```

### 3. Add the orange fragment shader

```glsl [src/main/resources/assets/lightworkshop/shaders/core/pedestal_glow.fsh]
#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    color.rgb = mix(color.rgb, color.rgb * vec3(1.35, 0.75, 0.35), 0.45);

    fragColor = apply_fog(
            color,
            sphericalVertexDistance,
            cylindricalVertexDistance,
            FogEnvironmentalStart,
            FogEnvironmentalEnd,
            FogRenderDistanceStart,
            FogRenderDistanceEnd,
            FogColor);
}
```

`ALPHA_CUTOUT` exists because the JSON definition supplies `alpha_cutout`.

### 4. Resolve the reloadable pipeline

Create an exact Java fallback during client initialization. The JSON layer takes precedence after
resources load and after F3+T:

```java [src/client/java/dev/example/lightworkshop/render/ClientPipelines.java]
package dev.example.lightworkshop.render;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.minecraft.resources.ResourceLocation;

public final class ClientPipelines {
    private static final ResourceLocation PEDESTAL_GLOW = id("pedestal_glow");

    private ClientPipelines() {
    }

    public static void initialize() {
        pedestalGlow();
    }

    public static GluePipeline pedestalGlow() {
        return GlueClientRegistries.PIPELINES.getOrRegister(PEDESTAL_GLOW, () ->
                GluePipeline.entity(
                        PEDESTAL_GLOW,
                        id("core/entity"),
                        id("core/pedestal_glow")));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("lightworkshop", path);
    }
}
```

Call `ClientPipelines.initialize()` once from `LightWorkshopClient.onInitializeClient()`.

### 5. Draw through the pipeline

Replace the normal buffer source only around the displayed item. The method signature is the current
Mojang-mapped `BlockEntityRenderer` contract:

```java [src/client/java/dev/example/lightworkshop/render/PedestalBlockEntityRenderer.java]
@Override
public void render(LumenPedestalBlockEntity entity, float tickDelta, PoseStack poseStack,
                   MultiBufferSource buffers, int packedLight, int packedOverlay,
                   Vec3 cameraPos) {
    if (RenderCompat.isRenderingShadowPass()) return;

    poseStack.pushPose();
    try {
        poseStack.translate(0.5, 1.15, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                (entity.getLevel().getGameTime() + tickDelta) * 2.0F));
        poseStack.scale(0.75F, 0.75F, 0.75F);

        try (ShadedBufferSource shaded = ClientPipelines.pedestalGlow().wrap()) {
            itemRenderer.renderStatic(
                    DISPLAY_ITEM,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    shaded,
                    entity.getLevel(),
                    (int) entity.getBlockPos().asLong());
        }
    } finally {
        poseStack.popPose();
    }
}
```

The copper display keeps its texture and lighting, gains a warm orange tint, and remains fogged with
the scene. With an active Iris pack, Glue captures and composites this custom draw through its
managed compatibility path.

## Own the Draw Correctly

`ShadedBufferSource` owns native vertex buffers. Always use try-with-resources. `close()` flushes a
pending batch and releases every buffer even when drawing throws. Call `endBatch()` only for an
intentional mid-scope flush; a later `close()` will not flush the same batch twice.

The wrapper accepts textured `RenderType.CompositeRenderType` inputs. It extracts the texture,
selects the pipeline's category-specific render type, and replaces the caller's other composite
state. Unsupported or untextured types are dropped and logged at debug level instead of being drawn
with the wrong vertex contract.

Resolve `ClientPipelines.pedestalGlow()` when drawing rather than storing the returned
`GluePipeline`. F3+T bakes new JSON instances; a retained instance does not become the new pipeline.

## Handle Iris and Render State

- Keep the `RenderCompat.isRenderingShadowPass()` guard unless this custom draw was explicitly
  designed for the Iris shadow pass. `ShadedBufferSource` does not suppress that second callback.
- With an active Iris pack, `wrap()` captures with Iris bypassed and composites after world
  rendering. Additive pipelines use additive compositing. Without an active pack, ordinary
  non-additive draws render directly.
- The managed path restores the GL state it changes. Any raw GL or custom state added around it still
  belongs to your renderer and must be restored completely.
- Build/register Java fallbacks during client initialization. Resolve and draw on the client render
  thread; pipeline render-type caches and buffer sources are render-thread confined.

::: details Pipeline JSON schema

Only `vertex_shader` and `fragment_shader` are required. `category` selects standard render state; it
does not change the snippet, vertex format, samplers, or Iris program to another preset.

| Field | Default | Accepted value or behavior |
| --- | --- | --- |
| `vertex_shader` | Required | Core vertex shader ID without `shaders/` or extension. |
| `fragment_shader` | Required | Core fragment shader ID without `shaders/` or extension. |
| `snippet` | `matrices_fog_light_dir` | `matrices_projection`, `fog`, `globals`, `matrices_fog`, `matrices_fog_light_dir`, `terrain`, `entity`, `entity_emissive`, `particle`, `gui`, or `gui_textured`. |
| `vertex_format` | `new_entity` | `block`, `new_entity`, `particle`, `position`, `position_color`, `position_tex`, or `position_tex_color`. |
| `vertex_mode` | `QUADS` | Current `VertexFormat.Mode` name, case-insensitive. |
| `blend` | No blending | `lightning`, `glint`, `overlay`, `translucent`, `translucent_premultiplied_alpha`, `additive`, `entity_outline_blit`, or `invert`. |
| `alpha_cutout` | No define | Adds numeric shader define `ALPHA_CUTOUT`. |
| `cull` | `false` | Enables or disables face culling. |
| `samplers` | `Sampler0`, `Sampler1`, `Sampler2` | Replaces the complete sampler list. |
| `iris_program` | `ENTITIES_TRANSLUCENT` | Exact Iris `IrisProgram` enum name when Iris is installed. |
| `category` | `entity` | `entity`, `block`, or `particle`. |

JSON does not expose color-write, depth-write, depth-test, or depth-bias controls. Use the Java
builder for those specialized passes.
:::

::: details Presets, render types, and Java-only controls

`GluePipeline.builder(...)` begins with the entity preset: matrices/fog/light directions,
`NEW_ENTITY / QUADS`, translucent blending, alpha cutout `0.1`, culling off, color/depth writes on,
default `LEQUAL` depth behavior, no depth bias, three entity samplers, Iris program
`ENTITIES_TRANSLUCENT`, and category `ENTITY`.

`block(...)` changes to matrices/fog, `BLOCK / QUADS`, samplers `Sampler0` and `Sampler2`, no alpha
define, Iris `TERRAIN`, and category `BLOCK`. `particle(...)` uses the analogous particle format,
Iris `PARTICLES`, and category `PARTICLE`.

The builder exposes `snippet`, `vertexFormat`, `blend`/`noBlend`, `cull`, `colorWrite`, `depthWrite`,
`depthTest`, `depthBias`, `alphaCutout`/`noAlphaCutout`, `irisProgram`, `category`, `samplers`, and
`addSampler`. `entityCustom` is deprecated for removal.

`renderType(texture)` dispatches by category. Entity state includes texture, light map, and overlay;
block and particle state include texture and light map. Explicit `entityType`, `blockType`, and
`particleType` methods bypass category dispatch. Custom state overloads accept a stable `stateKey`
and optional `sortOnUpload`; each pipeline keeps an LRU cache of at most 16 render types.
:::

::: details Raw pipelines and isolated capture

Use `CoreShaderRegistry.registerRaw` when a Minecraft API accepts a `RenderPipeline` directly and no
buffer wrapping is needed. Raw pipelines are not entries in `GlueClientRegistries.PIPELINES` and do
not gain `ShadedBufferSource` capture or compositing.

`wrapIsolated()` captures into Glue's shared isolated target without compositing. The target from
`ShaderContext.get().getIsolatedTarget()` is borrowed: do not destroy it or retain its texture or
framebuffer IDs across a resize.
:::

## Troubleshoot the Result

- **Nothing draws:** confirm the source renderer requests a textured composite `RenderType` and the
  shader inputs match the selected vertex format.
- **`ALPHA_CUTOUT` fails compilation:** define `alpha_cutout`, call `alphaCutout` in Java, or stop
  using the define and select `noAlphaCutout`.
- **Blocks or particles have wrong attributes:** category changes state only. Select the matching
  snippet, format, samplers, and Iris program, or use the corresponding Java factory.
- **The object appears in an Iris shadow or twice:** add the shadow-pass guard from the draw example.
- **F3+T appears ineffective:** re-resolve the registry ID instead of retaining the old baked object.

## Next Steps

- Process the completed world with [Post-Processing Effects](./post-effects.md).
- Inspect pipeline outputs with the [Framebuffer Debug HUD](./debug-hud.md).
- Use the consumer decision tree in [Optional-mod Compatibility](./compatibility.md) before adding
  any direct Iris check beyond the shadow guard.
