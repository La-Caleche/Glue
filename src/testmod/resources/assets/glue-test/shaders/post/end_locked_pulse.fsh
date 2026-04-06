#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform PulseConfig {
    float Progress;
};

in vec2 texCoord;
out vec4 fragColor;

float smootherStep(float t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

void main() {
    vec2 uv = texCoord;

    float decay = 1.0 - Progress;
    float eased = smootherStep(decay);

    vec2 centered = uv - 0.5;
    float dist = length(centered);

    float splitDist = max(0.0, 1.0 - eased * 3.0) * 0.04;
    float r = texture(InSampler, clamp(uv + vec2(splitDist, 0.0), 0.0, 1.0)).r;
    float g = texture(InSampler, uv).g;
    float b = texture(InSampler, clamp(uv - vec2(splitDist, 0.0), 0.0, 1.0)).b;
    vec4 col = vec4(r, g, b, 1.0);

    float vignette = 1.0 - smoothstep(0.1, 0.6, dist) * (1.0 - eased) * 1.5;
    col.rgb *= max(0.0, vignette);

    float tintPower = (1.0 - eased) * 0.65;
    col.rgb = mix(col.rgb, vec3(0.8, 0.0, 0.1), tintPower);

    float wave = smoothstep(0.15, 0.0, abs(dist - eased * 0.6));
    float waveAlpha = wave * max(0.0, 1.0 - eased * 1.5) * 0.5;
    col.rgb += vec3(0.6, 0.1, 0.8) * waveAlpha;

    fragColor = col;
}
