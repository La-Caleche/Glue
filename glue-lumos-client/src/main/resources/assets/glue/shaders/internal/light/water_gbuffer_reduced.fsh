#version 150

#extension GL_ARB_explicit_attrib_location : require

// Reduced-frame water capture: identical outputs to water_gbuffer.fsh, except the owner depth is
// the TRUE (un-pulled) surface depth forwarded by water_gbuffer_reduced.vsh -- the rasterised
// depth is pulled toward the camera to survive the pack's wave displacement, and packing that
// would put the pull distance into every downstream ownership test.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
noperspective in float glueTrueDepth;

layout(location = 1) out vec4 glue_Material;
layout(location = 2) out vec4 glue_MaterialId;
layout(location = 3) out vec4 glue_MaterialProps;

vec3 srgbToLinear(vec3 color) {
    vec3 low = color / 12.92;
    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), color));
}

vec3 gluePackDepth24(float depth) {
    float value = floor(clamp(depth, 0.0, 1.0) * 16777215.0 + 0.5);
    float high = floor(value / 65536.0);
    value -= high * 65536.0;
    float middle = floor(value / 256.0);
    float low = value - middle * 256.0;
    return vec3(high, middle, low) / 255.0;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    glue_Material = vec4(srgbToLinear(color.rgb), color.a);
    glue_MaterialId = vec4(5.0 / 255.0, gluePackDepth24(glueTrueDepth));
    // Water: near-mirror dielectric, F0 ~0.02 (its real Fresnel reflectance).
    glue_MaterialProps = vec4(0.02, 0.0, 0.02, 1.0);
}
