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
const float TINT_STRENGTH = 1.4;

void main() {
    // Top mip, not the texel: the projected shadow should be one flat colour per pane,
    // not a slide of the pane's border-and-dashes texture. The atlas's highest mip IS
    // the sprite's average colour, so no colour table has to be maintained. (With
    // mipmaps disabled in settings this falls back to the base level -- textured tint,
    // cosmetic only.)
    vec4 color = textureLod(Sampler0, texCoord0, 8.0) * vertexColor;

    // A fully transparent texel transmits everything; it must not claim the texel's
    // depth and hide a real pane behind it.
    if (color.a < 0.01) discard;

    float opacity = clamp(color.a * TINT_STRENGTH, 0.0, 1.0);
    vec3 transmittance = mix(vec3(1.0), color.rgb, opacity);

    fragColor = vec4(transmittance, 1.0);
}
