#version 150

// -----------------------------------------------------------------------------
// Example: Additive Sprite (Fragment Shader)
// Description: Renders bright additive pixels and properly discards
// near-black pixels to prevent the "black box occlusion" bug in Iris.
// Applies a custom additive fog effect.
// -----------------------------------------------------------------------------

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
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }

    // ADDITIVE FOG: multiply by (1 - fogFactor) instead of mixing with FogColor.
    float fogFactor = total_fog_value(
        sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd
    );
    color.rgb *= 1.0 - fogFactor;

    // Discard near-black pixels by luminance.
    // In additive blending, black (0,0,0) adds nothing visually, but it
    // still writes depth. Discarding ensures no depth write for these pixels.
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    if (luminance < 0.01) {
        discard;
    }

    fragColor = color;
}
