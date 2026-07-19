#version 150

#extension GL_ARB_explicit_attrib_location : require

// Water into the shared material G-buffer. Nearby water surfaces are re-rasterised from the camera
// with the frame's exact matrices and depth-tested (read-only) against the main depth, so only the
// frontmost surface survives. Attachment 0 (the main colour) is left untouched -- vanilla's own
// translucent pass already blended the water in -- so this writes only the material outputs:
//
//   location 1 (RGBA16F): linear albedo (RGB) + opacity (A). The albedo carries the biome water
//     colour; the deferred pass derives the surface normal from depth (water is flat) and perturbs
//     it with procedural ripples.
//   location 2 (RGBA8): material id 5 (WATER) in R + this surface's window depth packed into GBA.
//   location 3 (RGBA8): roughness / metalness / F0. Water is a near-mirror dielectric.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float lightNormDist;

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
    glue_MaterialId = vec4(5.0 / 255.0, gluePackDepth24(gl_FragCoord.z));
    // Water: near-mirror dielectric, F0 ~0.02 (its real Fresnel reflectance).
    glue_MaterialProps = vec4(0.02, 0.0, 0.02, 1.0);
}
