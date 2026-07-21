#version 150

// Vertex stage for the reduced-frame water capture. A shaderpack that owns the scene depth may
// displace its water with waves, so the flat vanilla fluid geometry this pass rasterises would
// fail the read-only LEQUAL test wherever a crest is closer than the flat plane -- holes in the
// capture. Pull every vertex toward the camera along its view ray (screen position is unchanged,
// only depth moves) so the test passes over crests, while anything more than the pull distance
// behind an occluder still fails -- walls keep occluding.
//
// The TRUE (un-pulled) window depth rides along as a noperspective varying (exactly hardware's
// screen-linear depth interpolation for planar water quads): the fragment stage packs it as the
// owner depth, so the ownership test downstream compares the real flat surface against the pack's
// displaced one and needs only a wave-amplitude margin, not the pull distance.

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
noperspective out float glueTrueDepth;

// How far toward the camera the rasterised surface is pulled. Must exceed the pack's wave
// amplitude; anything behind an occluder by more than this still fails the depth test.
const float WAVE_PULL = 0.3;

void main() {
    // Geometry is camera-relative world space (the camera is the origin), so the view ray of a
    // vertex is its own direction.
    vec3 pulled = Position - normalize(Position) * min(WAVE_PULL, length(Position) * 0.5);
    gl_Position = ProjMat * ModelViewMat * vec4(pulled, 1.0);

    vec4 trueClip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    glueTrueDepth = (trueClip.z / trueClip.w) * 0.5 + 0.5;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = Color;
    texCoord0 = UV0;
}
