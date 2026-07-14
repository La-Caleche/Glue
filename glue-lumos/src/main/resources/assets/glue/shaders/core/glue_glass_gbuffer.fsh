#version 150

// Glass G-buffer: nearby translucent blocks rendered from the CAMERA with the frame's
// exact matrices, into a private albedo + depth target. The deferred light pass then
// identifies "the visible surface here is glass" by comparing this depth against the
// scene depth -- a geometric test that works on every face, including the ray-grazing
// ones where the tint map's per-texel distance heuristic falls apart -- and colours the
// glow with this albedo, the pane's OWN texture, instead of whatever the tint map
// projects through it.
//
// No alpha cutout: vanilla's translucent pass writes depth for fully transparent texels
// too, and this buffer has to match the scene depth texel-for-texel or the equality
// test punches holes in the middle of clear glass. Alpha is kept in the attachment so
// the deferred pass can turn it into transmittance (a transparent texel glows white,
// not black).

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float lightNormDist;

out vec4 fragColor;

vec3 srgbToLinear(vec3 color) {
    vec3 low = color / 12.92;
    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), color));
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    fragColor = vec4(srgbToLinear(color.rgb), color.a);
}
