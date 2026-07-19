#version 150

// One axis of a separable Gaussian blur. Run horizontally then vertically (twice, with a
// growing step) for a wide, soft bloom. Runs at bloom resolution, so a few taps cover a
// large screen-space radius cheaply.

uniform sampler2D Source;
uniform vec2 Direction;   // texel step along one axis, already scaled by the pass radius

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float w0 = 0.227027;
    float w1 = 0.194595;
    float w2 = 0.121622;
    float w3 = 0.054054;
    float w4 = 0.016216;

    vec3 c = texture(Source, texCoord).rgb * w0;
    c += texture(Source, texCoord + Direction * 1.0).rgb * w1;
    c += texture(Source, texCoord - Direction * 1.0).rgb * w1;
    c += texture(Source, texCoord + Direction * 2.0).rgb * w2;
    c += texture(Source, texCoord - Direction * 2.0).rgb * w2;
    c += texture(Source, texCoord + Direction * 3.0).rgb * w3;
    c += texture(Source, texCoord - Direction * 3.0).rgb * w3;
    c += texture(Source, texCoord + Direction * 4.0).rgb * w4;
    c += texture(Source, texCoord - Direction * 4.0).rgb * w4;
    fragColor = vec4(c, 1.0);
}
