---
title: Post-Processing Effects
description: Build and toggle a complete grayscale post chain, then advance to timed UBO control.
artifact: glue-render
modId: glue-render
environment: client
---

# Post-Processing Effects

This task adds a toggleable grayscale view to Light Workshop. Use a post effect when the completed
world color should be processed as an image; use a [rendering pipeline](./pipelines.md) instead when
only selected geometry needs a different core shader.

## Build the Grayscale Effect

The smallest Java-registered effect needs one Minecraft post chain and one fragment shader:

```text
assets/lightworkshop/post_effect/grayscale.json
assets/lightworkshop/shaders/post/grayscale.fsh
```

### 1. Create the post chain

The first pass reads the main target into `swap`. The second uses Minecraft's blit shader to copy
`swap` back to the main target:

```json [src/main/resources/assets/lightworkshop/post_effect/grayscale.json]
{
  "targets": {
    "swap": {}
  },
  "passes": [
    {
      "vertex_shader": "minecraft:post/blit",
      "fragment_shader": "lightworkshop:post/grayscale",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "minecraft:main",
          "bilinear": false
        }
      ],
      "output": "swap"
    },
    {
      "vertex_shader": "minecraft:post/blit",
      "fragment_shader": "minecraft:post/blit",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "swap"
        }
      ],
      "output": "minecraft:main",
      "uniforms": {
        "BlitConfig": [
          {
            "name": "ColorModulate",
            "type": "vec4",
            "value": [1.0, 1.0, 1.0, 1.0]
          }
        ]
      }
    }
  ]
}
```

### 2. Convert the color

The input named `In` becomes `InSampler` in GLSL. `SamplerInfo` is part of Minecraft's current post
shader contract:

```glsl [src/main/resources/assets/lightworkshop/shaders/post/grayscale.fsh]
#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(vec3(gray), color.a);
}
```

### 3. Register and toggle it

Own one renderer for the client process and register it exactly once:

```java [src/client/java/dev/example/lightworkshop/render/LightWorkshopEffects.java]
package dev.example.lightworkshop.render;

import fr.lacaleche.glue.client.shader.GluePostEffectRenderer;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.registries.PostShaderRegistry;

public final class LightWorkshopEffects {
    private static final PostShaderRegistry POST_SHADERS =
            new PostShaderRegistry("lightworkshop");
    private static final PostShaderHandle GRAYSCALE =
            POST_SHADERS.register("grayscale");
    private static final GluePostEffectRenderer EFFECTS =
            new GluePostEffectRenderer();

    private LightWorkshopEffects() {
    }

    public static void initialize() {
        EFFECTS.register();
    }

    public static boolean toggleGrayscale() {
        return EFFECTS.toggle(GRAYSCALE);
    }
}
```

Call `LightWorkshopEffects.initialize()` from `LightWorkshopClient.onInitializeClient()`. Invoke
`toggleGrayscale()` from an existing client-thread key, button, or command callback. The first call
returns `true` and enables the effect; the next returns `false` and disables it.

With the effect active, terrain and entities are grayscale. The first-person hand, screen effects,
HUD status, screens, and toasts remain in color because Glue applies the chain earlier in the frame.

## Keep the Basic Toggle Basic

`toggle(handle)` owns membership in the active list. `isToggled(handle)` reads that state.
`addToggle(handle)` is different: it immediately adds an active effect, so do not call it as a
registration step for an initially disabled toggle. Do not add the same handle more than once.

`PostShaderRegistry.register("grayscale")` uses the main target set and registers a permanent handle
for `lightworkshop:grayscale`. Resource loading remains lazy: a missing or invalid chain makes
`PostShaderHandle.get()` return `null`; application logs once and skips it.

## Advance to Timed UBO Control

Use a separate chain ID for a timed fade so its last uniform value cannot change the basic toggle.
The following advanced recipe starts fully grayscale and fades to normal over 20 client ticks.

::: details Complete timed grayscale variant

Register the data-driven handle:

```json [src/main/resources/assets/lightworkshop/glue/post_chains/grayscale_fade.json]
{}
```

Define its duration and one-float UBO:

```json [src/main/resources/assets/lightworkshop/glue/post_effects/grayscale_fade.json]
{
  "post_chain": "lightworkshop:grayscale_fade",
  "ubo_name": "GrayscaleConfig",
  "ubo_size": 4,
  "duration": 20,
  "curve": "reverse"
}
```

Copy the basic post chain to `post_effect/grayscale_fade.json`, change its first fragment shader to
`lightworkshop:post/grayscale_fade`, and add this `uniforms` object to the first pass:

```json
"uniforms": {
  "GrayscaleConfig": [
    {
      "name": "Strength",
      "type": "float",
      "value": 1.0
    }
  ]
}
```

Use this complete timed fragment shader:

```glsl [src/main/resources/assets/lightworkshop/shaders/post/grayscale_fade.fsh]
#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GrayscaleConfig {
    float Strength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(color.rgb, vec3(gray), Strength), color.a);
}
```

Trigger the data-defined instance through the same registered renderer:

```java
private static final ResourceLocation GRAYSCALE_FADE =
        ResourceLocation.fromNamespaceAndPath("lightworkshop", "grayscale_fade");

public static void triggerGrayscaleFade() {
    EFFECTS.triggerTimed(GRAYSCALE_FADE);
}
```

`triggerTimed` bakes on first use and restarts an active instance. `isTimedActive(id)` queries it;
`stopTimed(id)` stops an instance that has already been baked. After a resource reload, the next
`triggerTimed` or `isTimedActive` call detects the new registry version, stops stale data-driven
instances, and makes the next trigger use the new definition. A reload alone does not eagerly remove
an active cached instance.
:::

The named curves are `linear`, `reverse`, `ease_in`, `ease_out`, and `ease_in_out`. Data-defined
timed effects write one progress float. Use a Java `TimedPostEffect` for custom curves or multiple
scalar values, and use `PostShaderHandle.setUniform` for other std140 types.

::: details Resource layers and reloads

A fully data-driven effect can use four resource layers:

| Path | Role |
| --- | --- |
| `post_effect/<name>.json` | Minecraft targets, passes, shaders, inputs, outputs, and UBO fields. |
| `shaders/post/<name>.fsh` | Fragment shader referenced by the Minecraft chain. |
| `glue/post_chains/<name>.json` | Reloadable `PostShaderHandle`; optional `external_targets` defaults to main targets. |
| `glue/post_effects/<name>.json` | Optional duration, curve, post-chain ID, and one-float UBO writer. |

JSON handles and timed definitions replace their reloadable registry layers on F3+T and override
permanent Java entries with the same ID. Java registrations survive reloads. Re-resolve JSON handles
when their external targets may have changed.

Minecraft 1.21.8 uses named std140 UBO blocks with ordered field definitions. The old scalar-uniform
post format is not valid.
:::

::: details Java TimedPostEffect ownership

Build a direct instance when JSON cannot express the writer:

```java
TimedPostEffect pulse = TimedPostEffect.builder(handle)
        .ubo("PulseConfig", 16)
        .duration(20)
        .curveReverse()
        .uniforms(writer -> writer
                .putProgress()
                .putFloat(0.05F)
                .putFloat(0.8F)
                .putFloat(0.0F))
        .build();

EFFECTS.addTimed(pulse);
pulse.trigger();
```

`addTimed` attaches but does not activate. `trigger` starts or restarts, `stop` deactivates, and
`removeTimed` detaches a directly owned instance. Match `ubo_size` to both the ordered chain fields
and std140 layout. `UniformWriter` writes scalar floats only; four floats require 16 bytes.
:::

## Respect Ordering, Threads, and Iris

- Register renderers and mutate toggle/timed collections on the client thread. Handle lookup,
  uniform replacement, and chain processing touch render resources.
- Timed effects advance at `END_CLIENT_TICK`, pause while the client is paused, and render only with
  a player and level present.
- Active toggles apply in insertion order, followed by active timed effects in insertion order. Each
  effect receives the output of the previous one.
- Rendering runs in the default [`POST_WORLD_RENDER`](./events.md#use-post-world-rendering-safely)
  phase after capture and Lumos lighting, before the hand and HUD.
- The managed renderer skips Iris shadow passes. `PostShaderHandle.apply` preserves touched GL state,
  transfers the active Iris framebuffer when needed, and runs the chain under Iris bypass.
- `register()` attaches permanent tick and render listeners and throws if called twice on the same
  renderer. There is no unregister operation. The default resource-pool depth is three frames.

Manual chain application requires one retained `CrossFrameResourcePool`, one `endFrame()` after all
chains each frame, a shadow-pass guard, and render-thread execution. Prefer
`GluePostEffectRenderer` unless custom ordering requires that ownership.

## Troubleshoot the Effect

- **Post chain not available:** verify the `post_effect` path, shader IDs, JSON syntax, and external
  targets. Glue warns once per ID until resource reload.
- **Timed definition will not trigger:** register its `post_chain` in Java or under
  `glue/post_chains`.
- **Uniform does not change:** match block name, field order, field types, byte size, and GLSL std140
  declaration exactly.
- **Effect starts enabled:** replace `addToggle` with `toggle` for an initially disabled control.
- **Hand or HUD stays colored:** this is the documented post-world placement, not a failed chain.

## Next Steps

- Inspect the main and depth targets in [Framebuffer Debug HUD](./debug-hud.md).
- Review [Optional-mod Compatibility](./compatibility.md) before manually applying a chain.
- Present the workshop from another camera in [3D Scene Viewport](./scene-viewport.md).
