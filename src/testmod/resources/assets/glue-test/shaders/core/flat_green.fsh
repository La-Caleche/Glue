#version 150
// Flat Green — pure solid color, no shading, no alpha tricks.
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < ALPHA_CUTOUT) discard;
    fragColor = vec4(0.0, 1.0, 0.0, 1.0);
}
