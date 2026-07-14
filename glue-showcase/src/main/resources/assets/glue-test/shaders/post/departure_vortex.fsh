#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VortexConfig {
    float Progress;
};

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.14159265358979323846

vec2 rotate(vec2 uv, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c) * uv;
}

float smootherStep(float t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

void main() {
    vec2 uv = texCoord;
    vec2 centered = uv - 0.5;

    float dist   = length(centered);
    float eased  = smootherStep(Progress);

    float maxAngle = PI * eased;

    float radialFactor = clamp(1.0 - dist * 1.8, 0.0, 1.0);
    float angle = maxAngle * (1.0 - radialFactor);

    vec2 warped  = rotate(centered, angle) + 0.5;

    float pullStrength = eased * 0.08;
    warped = mix(warped, vec2(0.5), pullStrength);

    vec4 col = texture(InSampler, clamp(warped, 0.0, 1.0));

    float edgeTint = smoothstep(0.25, 0.55, dist) * eased;
    vec3 vortexColor = vec3(0.35, 0.1, 0.85);
    col.rgb = mix(col.rgb, vortexColor, edgeTint * 0.65);

    float fringeAmt = eased * 0.012;
    float r = texture(InSampler, clamp(warped + vec2(fringeAmt, 0.0), 0.0, 1.0)).r;
    float b = texture(InSampler, clamp(warped - vec2(fringeAmt, 0.0), 0.0, 1.0)).b;
    col.r = mix(col.r, r, eased * 0.6);
    col.b = mix(col.b, b, eased * 0.6);

    float vignette = 1.0 - smoothstep(0.3, 0.7, dist) * eased * 0.7;
    col.rgb *= vignette;

    fragColor = col;
}
