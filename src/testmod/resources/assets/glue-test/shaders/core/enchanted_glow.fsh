#version 150

// Enchanted Glow — Purple/gold pulsing enchantment aura with shimmer.

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
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    // ── Enchanted glow effect ────────────────────────────────
    // Time from GameTime (use worldPos as a pseudo-time with slow movement)
    float t = worldPos.y * 3.0 + worldPos.x * 1.5;

    // Shifting purple/gold gradient
    vec3 purple = vec3(0.6, 0.1, 0.9);
    vec3 gold   = vec3(1.0, 0.8, 0.2);
    float blend = sin(t * 2.0) * 0.5 + 0.5;
    vec3 enchantTint = mix(purple, gold, blend);

    // Apply enchant as additive glow
    float glowStrength = 0.4 + 0.2 * sin(t * 5.0);
    color.rgb = mix(color.rgb, color.rgb + enchantTint * 0.5, glowStrength);

    // Brighten overall
    color.rgb *= 1.3;
    color.rgb = clamp(color.rgb, 0.0, 1.0);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
