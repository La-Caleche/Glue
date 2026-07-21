#version 150

// Vertex stage shared by both shadow-bake passes (depth and tint). Blocks are
// submitted in light-relative coordinates, so ModelViewMat is the light's view.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float lightNormDist;
// World-space face normal, consumed only by terrain_gbuffer.fsh (the material G-buffer packs it);
// the other fragment stages sharing this vertex stage simply do not declare the input.
out vec3 glueNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // Unused, but the MATRICES_FOG snippet binds the fog block and the shader has to
    // declare it. There is no fog in a shadow map.
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    // Geometry is submitted in LIGHT-RELATIVE coordinates, so Position is already the
    // offset from the light and its length is the distance the light travels to get
    // here. Normalising by the frustum's far plane -- which is the light's range --
    // puts it in 0..1 so the tint pass can pack it into an 8-bit alpha channel.
    // Recovering far from the projection: for a right-handed perspective matrix,
    // m32 / (m22 + 1) == far.
    float far = ProjMat[3][2] / (ProjMat[2][2] + 1.0);
    lightNormDist = clamp(length(Position) / max(far, 1e-4), 0.0, 1.0);

    vertexColor = Color;   // no lighting: a shadow map only cares about geometry
    texCoord0 = UV0;
    glueNormal = Normal;
}
