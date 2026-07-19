#version 150

#extension GL_ARB_explicit_attrib_location : require

// Metal into the shared material G-buffer. Nearby curated metal blocks are re-rasterised from the
// camera with the frame's exact matrices and depth-tested (read-only) against the main depth, so only
// the frontmost surface survives, overwriting the terrain id (1) with the metal id (6) for those
// pixels. Attachment 0 (the main colour) is left untouched -- vanilla already drew the opaque block.
//
//   location 1 (RGBA16F): linear albedo (RGB) -- the metal's own colour, which tints its reflection.
//   location 2 (RGBA8): material id 6 (METAL) in R + this surface's window depth packed into GBA.
//   location 3 (RGBA8): roughness / metalness / F0. Metal: metalness = 1, so the deferred pass uses
//     the albedo as the specular colour (a gold block reflects gold).

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
    glue_Material = vec4(srgbToLinear(color.rgb), 1.0);
    glue_MaterialId = vec4(6.0 / 255.0, gluePackDepth24(gl_FragCoord.z));
    // Metal: metalness = 1, a moderate polished roughness. F0 stored high but the metallic path
    // uses the albedo as the reflectance colour.
    glue_MaterialProps = vec4(0.30, 1.0, 0.9, 1.0);
}
