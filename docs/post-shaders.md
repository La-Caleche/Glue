# Post-Processing Shaders

Glue wraps MC 1.21.8's `PostChain` system with `PostShaderHandle`, which handles lazy loading, dynamic uniform updates, and Iris compatibility.

## Registration

```java
public static final PostShaderRegistry POST = new PostShaderRegistry("mymod", MyMod::id);

public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");
public static final PostShaderHandle BLUR = POST.register("blur");
```

This loads from `assets/<modid>/post_effect/<name>.json`.

## Applying a Post Effect

```java
private static final CrossFrameResourcePool POOL = new CrossFrameResourcePool(3);

// In a WorldRenderEvents.LAST callback:
MY_SHADER.apply(mc.getMainRenderTarget(), POOL);
POOL.endFrame();
```

## Dynamic Uniforms (UBOs)

Update uniform buffer values at runtime:

```java
handle.setUniform("MyConfig", 16, builder -> {
    builder.putFloat(intensity);    // Must match GLSL struct order
    builder.putFloat(maxOffset);
    builder.putFloat(strength);
    builder.putFloat(flash);
});
```

The buffer size is `field_count × 4 bytes` for floats. The order must exactly match the `layout(std140)` block in your GLSL.

## JSON Format (PostChain)

```json
{
    "targets": {
        "swap": {}
    },
    "passes": [
        {
            "vertex_shader": "minecraft:post/blit",
            "fragment_shader": "mymod:post/my_effect",
            "inputs": [
                {
                    "sampler_name": "In",
                    "target": "minecraft:main",
                    "bilinear": false
                }
            ],
            "output": "swap",
            "uniforms": {
                "MyConfig": [
                    { "name": "Intensity", "type": "float", "value": 1.0 },
                    { "name": "Strength", "type": "float", "value": 0.5 }
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

uniform sampler2D InSampler;  // "In" + "Sampler" suffix

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MyConfig {
    float Intensity;
    float Strength;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    // ... your effect ...
    fragColor = color;
}
```

## Using Vanilla Shaders

You can reference vanilla MC shaders directly:

```json
{
    "vertex_shader": "minecraft:post/blur",
    "fragment_shader": "minecraft:post/box_blur"
}
```

## Texture Inputs

For shaders that need additional textures:

```json
{
    "sampler_name": "DataMap",
    "location": "mymod:textures/effect/data.png",
    "bilinear": false
}
```

Place textures at `assets/<modid>/textures/effect/<name>.png`.

## Multi-Pass Example (Blur)

Separable blur with horizontal + vertical passes:

```json
{
    "targets": { "swap": {} },
    "passes": [
        {
            "vertex_shader": "minecraft:post/blur",
            "fragment_shader": "minecraft:post/box_blur",
            "inputs": [{ "sampler_name": "In", "target": "minecraft:main", "bilinear": true }],
            "output": "swap",
            "uniforms": {
                "BlurConfig": [
                    { "name": "BlurDir", "type": "vec2", "value": [1.0, 0.0] },
                    { "name": "Radius", "type": "float", "value": 8.0 }
                ]
            }
        },
        {
            "vertex_shader": "minecraft:post/blur",
            "fragment_shader": "minecraft:post/box_blur",
            "inputs": [{ "sampler_name": "In", "target": "swap", "bilinear": true }],
            "output": "minecraft:main",
            "uniforms": {
                "BlurConfig": [
                    { "name": "BlurDir", "type": "vec2", "value": [0.0, 1.0] },
                    { "name": "Radius", "type": "float", "value": 8.0 }
                ]
            }
        }
    ]
}
```
