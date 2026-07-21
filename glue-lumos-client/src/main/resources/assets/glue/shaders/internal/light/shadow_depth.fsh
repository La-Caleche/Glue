#version 150

// Opaque shadow casters. Colour writes are disabled on this pipeline, so the only
// thing that matters is which fragments survive: the alpha cutout is what gives
// leaves, grates and panes their lacy shadows instead of solid blocks.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    fragColor = color;   // discarded by the pipeline's colour write mask
}
