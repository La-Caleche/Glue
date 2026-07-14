#version 150

// Translucent shadow casters -- glass, stained glass, ice, water.
//
// Rather than occluding the light (which would make glass cast a solid black shadow, the
// same as stone), translucent casters write the light's TRANSMITTANCE into the shadow
// map's colour attachment. This shader serves BOTH translucent passes; the pipeline's
// write masks decide which output survives:
//
//  - colour pass: every pane on the ray multiplies in (red behind blue merges to
//    violet -- transmittance composes by product, which also makes it order-independent),
//    depth-tested against the opaque map so a pane behind a wall tints nothing;
//  - depth pass: the nearest pane joins the opaque depth, forming the with-translucents
//    map. WHERE the glass starts is that real depth buffer, not a distance packed into
//    8-bit alpha; the deferred pass decides "has the light crossed glass before reaching
//    this receiver?" with a plain biased depth compare against it.
//
// Transmittance of a surface with colour C and opacity a is mix(1, C, a): clear glass
// leaves the light alone, red glass strips everything but red.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float lightNormDist;

out vec4 fragColor;

// Vanilla stained glass sits around 50% opacity, which alone tints far too weakly to
// read as coloured light. Push the opacity used for transmission -- physically this
// is saying the pane is thicker than one texel of alpha suggests.
const float TINT_STRENGTH = 1.8;

vec3 srgbToLinear(vec3 color) {
    vec3 low = color / 12.92;
    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), color));
}

void main() {
    // A standard 16x16 block sprite reaches one texel at mip 4. Sampling mip 8 crosses
    // sprite boundaries in the atlas and mixes unrelated blocks into the transmitted
    // colour. Mip 4 keeps the pane average stable without projecting its border pattern.
    vec4 color = textureLod(Sampler0, texCoord0, 4.0) * vertexColor;

    // A fully transparent texel transmits everything; it must not claim the texel's
    // depth and hide a real pane behind it.
    if (color.a < 0.01) discard;

    float opacity = clamp(color.a * TINT_STRENGTH, 0.0, 1.0);
    vec3 transmittance = mix(vec3(1.0), srgbToLinear(color.rgb), opacity);

    fragColor = vec4(transmittance, 1.0);
}
