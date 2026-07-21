#version 150

#extension GL_ARB_explicit_attrib_location : require

// Base terrain into the shared material G-buffer, for frames whose native capture cannot run (an
// Iris shaderpack frame, where the pack owns every terrain program). Nearby solid blocks are
// re-rasterised from the camera with the frame's exact matrices and depth-tested (read-only,
// camera-biased) against the scene depth, so only the frontmost surface claims its pixel under the
// TERRAIN id (1) -- restoring the real albedo the composite otherwise has to estimate from the
// pack's already-graded image. Attachment 0 (the main colour) is left untouched.
//
// Output conventions match the native terrain capture (CoreShaderMaterialPatch) exactly:
//   location 1 (RGBA16F): linear albedo (RGB) + octahedron-packed world normal (A).
//   location 2 (RGBA8): material id 1 (TERRAIN) in R + this surface's window depth packed into GBA.
//   location 3 (RGBA8): generic rough dielectric.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float lightNormDist;
in vec3 glueNormal;

layout(location = 1) out vec4 glue_Material;
layout(location = 2) out vec4 glue_MaterialId;
layout(location = 3) out vec4 glue_MaterialProps;

vec3 srgbToLinear(vec3 color) {
    vec3 low = color / 12.92;
    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), color));
}

vec2 signNotZero(vec2 v) {
    return vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}

float packNormal(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
    vec2 oct = n.xy;
    if (n.z < 0.0) oct = (vec2(1.0) - abs(oct.yx)) * signNotZero(oct);
    vec2 e = floor((oct * 0.5 + 0.5) * 15.0 + 0.5);
    return (e.x + e.y * 16.0) / 255.0;
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
    vec4 texel = texture(Sampler0, texCoord0);
    // A cut-out texel (leaves, plants) is not part of the surface and must not claim the pixel's
    // material; mirror vanilla's cutout threshold rather than the pipeline's, which is off so the
    // discard stays in one place.
    if (texel.a * vertexColor.a < 0.5) discard;

    // vertexColor carries biome/model tint x a scalar AO/face shade. Divide the shade out so the
    // tint keeps only its channel ratios, leaving reflectance -- the same convention the native
    // terrain capture uses, so a pixel looks identical whichever path captured it.
    float shade = max(vertexColor.r, max(vertexColor.g, vertexColor.b));
    vec3 tint = shade > 1e-4 ? vertexColor.rgb / shade : vec3(1.0);

    glue_Material = vec4(srgbToLinear(texel.rgb * tint), packNormal(normalize(glueNormal)));
    glue_MaterialId = vec4(1.0 / 255.0, gluePackDepth24(gl_FragCoord.z));
    glue_MaterialProps = vec4(1.0, 0.0, 0.04, 1.0);
}
