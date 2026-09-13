# Post-Processing Shaders

Glue provides a layered system for post-processing: **PostShaderHandle** (the chain itself), **TimedPostEffect** (an effect with duration/curve), and **GluePostEffectRenderer** (the lifecycle manager).

## Overview

```
PostShaderHandle       → wraps a vanilla PostChain (loaded from post_effect/<name>.json)
TimedPostEffect        → a PostShaderHandle + duration + UBO + curve (stateful, single-use)
TimedEffectDefinition  → data description of a TimedPostEffect (stored in registry)
GluePostEffectRenderer → manages toggles and timed effects, handles rendering lifecycle
```

---

## PostShaderHandle

A handle to a vanilla `PostChain`. Handles lazy loading and uniform updates.

### Java Registration

```java
public static final PostShaderRegistry POST = new PostShaderRegistry("mymod", MyMod::id);

public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");
public static final PostShaderHandle BLUR = POST.register("blur");
```

This expects a vanilla post chain JSON at `assets/<modid>/post_effect/<name>.json`.

### Data-Driven Registration (JSON)

Create `assets/<modid>/glue/post_chains/<name>.json`:

```json
{}
```

That's it. An empty object uses the default external targets (`minecraft:main`). Custom targets:

```json
{
    "external_targets": ["minecraft:main", "minecraft:entity_outline"]
}
```

These are loaded by `PostChainDefinitionLoader` into `GlueClientRegistries.POST_CHAINS`.

### Applying Manually

```java
private static final CrossFrameResourcePool POOL = new CrossFrameResourcePool(3);

// In a WorldRenderEvents.LAST callback:
MY_SHADER.apply(mc.getMainRenderTarget(), POOL);
POOL.endFrame();
```

### Dynamic Uniforms (UBOs)

```java
handle.setUniform("MyConfig", 16, builder -> {
    builder.putFloat(intensity);
    builder.putFloat(maxOffset);
    builder.putFloat(strength);
    builder.putFloat(flash);
});
```

Buffer size = `field_count × 4` bytes for floats. Order must match the GLSL `layout(std140)` block.

---

## TimedPostEffect

A stateful wrapper: plays an effect for N ticks with a progress curve and UBO updates.

### Java (custom lambdas)

```java
public static final TimedPostEffect CHROMATIC = TimedPostEffect.builder(MY_HANDLE)
        .ubo("ChromaticConfig", 4)
        .duration(15)
        .curve(t -> (1.0f - t) * 0.05f)
        .build();

// Trigger it
CHROMATIC.trigger();
```

For complex uniform writers:

```java
public static final TimedPostEffect SHATTERED = TimedPostEffect.builder(SHATTER_HANDLE)
        .ubo("ShatterConfig", 16)
        .duration(59)
        .curve(t -> t)
        .uniforms(w -> {
            float t = w.progress() * 59;
            w.putFloat(computeIntensity(t));
            w.putFloat(0.05f);
            w.putFloat(0.8f);
            w.putFloat(computeFlash(t));
        })
        .build();
```

### Data-Driven (JSON)

Create `assets/<modid>/glue/post_effects/<name>.json`:

```json
{
    "post_chain": "mymod:blur",
    "ubo_name": "BlurConfig",
    "ubo_size": 4,
    "duration": 20,
    "curve": "reverse"
}
```

| Field | Type | Default | Description |
|---|---|---|---|
| `post_chain` | `ResourceLocation` | required | ID of the PostShaderHandle (Java or JSON) |
| `ubo_name` | `string` | required | Name of the UBO in the GLSL shader |
| `ubo_size` | `int` | `4` | Buffer size in bytes |
| `duration` | `int` | `20` | Duration in ticks |
| `curve` | `string` | none | One of: `linear`, `reverse`, `ease_in`, `ease_out`, `ease_in_out` |

These are loaded into `GlueClientRegistries.TIMED_EFFECT_DEFINITIONS` as `TimedEffectDefinition` objects.

**Important:** JSON definitions store *data*, not baked instances. Call `.bake()` to get a stateful `TimedPostEffect`:

```java
TimedEffectDefinition def = GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.get(myId);
TimedPostEffect effect = def.bake();
effect.trigger();
```

> **Note:** Custom curve lambdas and complex uniform writers cannot be expressed in JSON. Use Java registration for those.

---

## GluePostEffectRenderer

Manages the lifecycle of toggleable and timed effects. Handles rendering each frame.

```java
private final GluePostEffectRenderer renderer = new GluePostEffectRenderer();

// Register effects
renderer
    .addTimed(MY_TIMED_EFFECT)
    .addTimed(ANOTHER_EFFECT)
    .register();

// Toggle a persistent effect
renderer.toggle(MY_HANDLE);    // returns new state (true/false)
renderer.isToggled(MY_HANDLE); // query state
```

---

## Vanilla PostChain JSON Format

The `PostShaderHandle` loads from `assets/<modid>/post_effect/<name>.json` (vanilla format):

```json
{
    "targets": { "swap": {} },
    "passes": [
        {
            "vertex_shader": "minecraft:post/blit",
            "fragment_shader": "mymod:post/my_effect",
            "inputs": [
                { "sampler_name": "In", "target": "minecraft:main", "bilinear": false }
            ],
            "output": "swap",
            "uniforms": {
                "MyConfig": [
                    { "name": "Intensity", "type": "float", "value": 1.0 }
                ]
            }
        },
        {
            "vertex_shader": "minecraft:post/blit",
            "fragment_shader": "minecraft:post/blit",
            "inputs": [{ "sampler_name": "In", "target": "swap" }],
            "output": "minecraft:main"
        }
    ]
}
```

## Fragment Shader Convention

```glsl
#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MyConfig {
    float Intensity;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    // ... your effect ...
    fragColor = color;
}
```

## File Locations

| What | Path |
|---|---|
| Vanilla PostChain JSON | `assets/<modid>/post_effect/<name>.json` |
| Glue post chain definition | `assets/<modid>/glue/post_chains/<name>.json` |
| Glue timed effect definition | `assets/<modid>/glue/post_effects/<name>.json` |
| Post-processing fragment shaders | `assets/<modid>/shaders/post/<name>.fsh` |
| Additional textures | `assets/<modid>/textures/effect/<name>.png` |
