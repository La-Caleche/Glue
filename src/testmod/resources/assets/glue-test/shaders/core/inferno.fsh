#version 150

// Inferno — Fiery lava/ember effect with heat distortion.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

// Simple noise for fire turbulence
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    // ── Inferno / Fire effect ────────────────────────────────
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Fire turbulence from FBM noise
    vec2 fireUV = worldPos.xy * 4.0;
    fireUV.y -= worldPos.y * 2.0; // upward flow
    float fire = fbm(fireUV);

    // Fire color ramp: black → red → orange → yellow → white
    float t = clamp(luminance * 1.5 + fire * 0.4, 0.0, 1.0);
    vec3 fireColor;
    if (t < 0.25) {
        fireColor = mix(vec3(0.1, 0.0, 0.0), vec3(0.8, 0.1, 0.0), t * 4.0);
    } else if (t < 0.5) {
        fireColor = mix(vec3(0.8, 0.1, 0.0), vec3(1.0, 0.5, 0.0), (t - 0.25) * 4.0);
    } else if (t < 0.75) {
        fireColor = mix(vec3(1.0, 0.5, 0.0), vec3(1.0, 0.9, 0.3), (t - 0.5) * 4.0);
    } else {
        fireColor = mix(vec3(1.0, 0.9, 0.3), vec3(1.0, 1.0, 0.9), (t - 0.75) * 4.0);
    }

    // Blend fire with base
    color.rgb = mix(color.rgb * 0.3, fireColor, 0.8);

    // Emissive glow — brighter than normal
    color.rgb *= 1.5;
    color.rgb = clamp(color.rgb, 0.0, 1.0);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
