#version 150

// Bright-pass + 4-tap downsample of the HDR light buffer. The light buffer holds Lumos'
// light contribution only, so blooming it glows the lit areas without touching vanilla's
// own bright pixels (moon, torches). Soft knee above the threshold keeps the transition
// from "no glow" to "glow" gradual instead of a hard cut.

uniform sampler2D LightTex;   // full-resolution HDR light
uniform vec2 SrcTexel;        // 1/fullWidth, 1/fullHeight
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 o = SrcTexel * 0.5;
    vec3 c = texture(LightTex, texCoord + vec2(-o.x, -o.y)).rgb
           + texture(LightTex, texCoord + vec2( o.x, -o.y)).rgb
           + texture(LightTex, texCoord + vec2(-o.x,  o.y)).rgb
           + texture(LightTex, texCoord + vec2( o.x,  o.y)).rgb;
    c *= 0.25;

    float luma = max(c.r, max(c.g, c.b));
    float keep = max(luma - Threshold, 0.0) / max(luma, 1e-4);
    fragColor = vec4(c * keep, 1.0);
}
