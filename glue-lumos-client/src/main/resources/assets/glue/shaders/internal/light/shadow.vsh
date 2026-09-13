#version 150

// Vertex stage shared by both shadow-bake passes (depth and tint). Blocks are
// submitted in light-relative coordinates, so ModelViewMat is the light's view.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
// World-space face normal, consumed only by terrain_gbuffer.fsh (the material G-buffer packs it);
// the other fragment stages sharing this vertex stage simply do not declare the input.
out vec3 glueNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;   // no lighting: a shadow map only cares about geometry
    texCoord0 = UV0;
    glueNormal = Normal;
}
