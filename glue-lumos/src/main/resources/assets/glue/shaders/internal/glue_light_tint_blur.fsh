#version 150

// Separable Gaussian over a shadow map's transmittance (glass tint) attachment.
//
// Run once per bake, not per pixel per frame -- the shadow maps are cached, so this is
// effectively free, and it can afford a radius wide enough to actually work. A per-pixel
// blur cannot: the artefact it has to remove is block-scale (the near-opaque border baked
// into every glass texture, which forms a hard cross where four block faces meet), and a
// dozen taps spread that wide only turns a crisp cross into a noisy one.
//
// Physically this is the diffusion light undergoes crossing a translucent surface.

uniform sampler2D Source;
uniform vec2 Direction;   // one texel along the axis being blurred, times the tap stride

in vec2 texCoord;
out vec4 fragColor;

// 13 taps, sigma ~= 3 strides.
const float WEIGHTS[7] = float[](
    0.1611, 0.1489, 0.1197, 0.0836, 0.0507, 0.0267, 0.0122
);

void main() {
    vec4 center = texture(Source, texCoord);

    vec3 sum = center.rgb * WEIGHTS[0];
    for (int i = 1; i < 7; i++) {
        vec2 offset = Direction * float(i);
        sum += texture(Source, texCoord + offset).rgb * WEIGHTS[i];
        sum += texture(Source, texCoord - offset).rgb * WEIGHTS[i];
    }

    // Colour blurs; ALPHA does not. Alpha is the distance to the blocker, and averaging a
    // distance across a silhouette produces a distance where no blocker exists -- which
    // would let the glass tint itself again right where its edges are.
    fragColor = vec4(sum, center.a);
}
