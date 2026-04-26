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
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    // Skip overlay — additive sprites don't use damage tint.
    // Skip lightmap — fullbright emissive rendering.

    // ADDITIVE FOG: multiply by (1 - fogFactor) instead of mixing with FogColor.
    // Standard apply_fog() does: mix(color, FogColor, fogFactor)
    // That adds sky-colored fog to black pixels -> creates the "black box".
    // Instead: color * (1 - fogFactor) -> distant pixels fade to (0,0,0).
    // Since this is additive: (0,0,0) + scene = scene. Box gone.
    float fogFactor = total_fog_value(
        sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd
    );
    color.rgb *= 1.0 - fogFactor;

    // Discard near-black pixels by luminance.
    // In additive blending, black (0,0,0) adds nothing visually, but it
    // still writes depth — blocking entities/clouds rendered later (the
    // "black box" artifact). Discarding ensures no depth write for these
    // pixels, while bright glow pixels still write depth correctly (needed
    // for Iris capture FBO → blit shader occlusion).
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    if (luminance < 0.01) discard;

    fragColor = color;
}
