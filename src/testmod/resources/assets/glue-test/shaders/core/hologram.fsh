#version 150

// Hologram fragment shader — applies a sci-fi hologram effect on top of
// vanilla entity rendering. Demonstrates how to iterate on the vanilla
// baseline to create custom visual effects.

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

void main() {
    // Sample the original texture (vanilla baseline)
    vec4 color = texture(Sampler0, texCoord0);

    // Alpha cutout (matches ALPHA_CUTOUT define in pipeline)
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }

    // Apply vanilla entity shading
    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    // ── Hologram effect ──────────────────────────────────────
    // Tint to cyan
    vec3 holoTint = vec3(0.3, 0.9, 1.0);
    color.rgb = mix(color.rgb, color.rgb * holoTint, 0.6);

    // Scanlines based on world Y position
    float scanline = sin(worldPos.y * 40.0) * 0.5 + 0.5;
    scanline = smoothstep(0.3, 0.7, scanline);
    color.rgb *= 0.7 + 0.3 * scanline;

    // Slight transparency for hologram feel
    color.a *= 0.8;

    // Apply fog (vanilla)
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
