#version 150

// Frozen — Ice blue with frost crystallization patterns.

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

// Simple hash for frost pattern
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

// Value noise
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

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    // ── Frozen / Ice effect ──────────────────────────────────
    // Desaturate towards white-blue
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 iceBlue = vec3(0.7, 0.85, 1.0);
    color.rgb = mix(vec3(luminance), iceBlue * luminance * 1.5, 0.7);

    // Frost crystal pattern from noise (world-space UV)
    vec2 frostUV = worldPos.xz * 8.0 + worldPos.y * 3.0;
    float frost = noise(frostUV);
    frost = smoothstep(0.3, 0.7, frost);

    // White frost highlights
    vec3 frostColor = vec3(0.9, 0.95, 1.0);
    color.rgb = mix(color.rgb, frostColor, frost * 0.35);

    // Subtle shimmer on edges
    float edge = 1.0 - abs(dot(normalize(worldPos), vec3(0.0, 1.0, 0.0)));
    edge = pow(edge, 2.0);
    color.rgb += vec3(0.3, 0.5, 0.7) * edge * 0.3;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
