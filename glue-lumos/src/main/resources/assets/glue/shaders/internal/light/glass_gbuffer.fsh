#version 150

#extension GL_ARB_explicit_attrib_location : require

// Glass into the shared material G-buffer. Nearby translucent panes are re-rasterised from the
// camera with the frame's exact matrices and depth-tested (read-only) against the main depth, so
// only the frontmost pane survives. Attachment 0 (the main colour) is left untouched -- vanilla's
// own translucent pass already blended the pane in -- so this writes only the material outputs:
//
//   location 1 (RGBA16F): linear albedo (RGB) + pane opacity (A). Opacity, NOT a packed normal:
//     panes are axis-aligned, so the deferred pass derives their normal from depth just as it
//     always has, and the more useful thing to carry here is the opacity that weights the pane's
//     scatter and tint response.
//   location 2 (RGBA8): material id 4 (GLASS) in R + this pane's window depth packed into GBA --
//     the same id + owner-depth contract terrain (1), entities (2) and particles (3) already use.

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
    glue_MaterialId = vec4(4.0 / 255.0, gluePackDepth24(gl_FragCoord.z));
}
