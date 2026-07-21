#version 150

// Bright-pass + 4-tap downsample of the HDR lit scene. This reads the composited lit buffer
// -- the visible on-screen brightness -- not the raw light field, so the glow tracks genuine
// highlights, including vanilla's own bright pixels (sun, moon), which bloom even where Lumos
// contributed no light. Soft knee above the threshold keeps the transition from "no glow" to
// "glow" gradual instead of a hard cut.

uniform sampler2D LitTex;     // full-resolution linear HDR scene + Lumos light
uniform vec2 SrcTexel;        // 1/fullWidth, 1/fullHeight
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 o = SrcTexel * 0.5;
    vec3 c = texture(LitTex, texCoord + vec2(-o.x, -o.y)).rgb
           + texture(LitTex, texCoord + vec2( o.x, -o.y)).rgb
           + texture(LitTex, texCoord + vec2(-o.x,  o.y)).rgb
           + texture(LitTex, texCoord + vec2( o.x,  o.y)).rgb;
    c *= 0.25;

    float luma = max(c.r, max(c.g, c.b));
    float keep = max(luma - Threshold, 0.0) / max(luma, 1e-4);
    fragColor = vec4(c * keep, 1.0);
}
