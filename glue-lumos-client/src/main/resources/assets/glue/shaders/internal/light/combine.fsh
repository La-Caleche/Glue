#version 150

// Final resolve: take the linear HDR lit scene, add the bloom built from its bright pixels,
// tonemap, and encode to sRGB for the display. Because the bloom was bright-passed from the
// visible brightness (this same lit buffer) rather than the raw light field, it glows what
// actually looks bright -- lit surfaces and highlights -- and leaves the rest alone.

uniform sampler2D LitTex;       // unit 0: linear HDR scene + Lumos light
uniform sampler2D BloomTex;     // unit 1: blurred bright pass (quarter res, bilinear)
uniform float BloomStrength;

// Highlight rolloff knee. Below it the tonemap is the identity (mid-tones and shadows are
// untouched); above it highlights compress toward 1 so bright light and bloom roll off
// softly instead of clipping to a flat white block.
const float KNEE = 0.7;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 lit = texture(LitTex, texCoord).rgb;
    vec3 bloom = texture(BloomTex, texCoord).rgb * BloomStrength;
    vec3 c = lit + bloom;
    // Keep a stray NaN/Inf from the HDR light math out of the display buffer -- it would show
    // as a black pixel here and, worse, get re-sampled into the glass by the reflection pass.
    if (any(isnan(c)) || any(isinf(c))) c = vec3(0.0);

    vec3 over = max(c - KNEE, 0.0);
    c = c - over + over / (1.0 + over / (1.0 - KNEE));

    vec3 low = c * 12.92;
    vec3 high = 1.055 * pow(max(c, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    vec3 srgb = mix(low, high, step(vec3(0.0031308), c));
    fragColor = vec4(clamp(srgb, vec3(0.0), vec3(1.0)), 1.0);
}
