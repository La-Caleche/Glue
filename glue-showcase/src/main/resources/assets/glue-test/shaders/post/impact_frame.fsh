#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ImpactConfig {
    float Threshold;
    float ThresholdLerp;
    float Invert;
    float MaxEstimatedGrayscale;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);

    float gray = max(max(color.r, color.g), color.b);

    gray /= max(MaxEstimatedGrayscale, 0.001);

    if (gray > Threshold) {
        gray = 1.0;
    } else {
        if (ThresholdLerp != 0.0) {
            float v = Threshold - gray;
            gray = smoothstep(0.0, 1.0, 1.0 - min(1.0, v / ThresholdLerp));
        } else {
            gray = 0.0;
        }
    }

    if (Invert > 0.0) {
        gray = 1.0 - gray;
    }

    fragColor = vec4(gray, gray, gray, color.a);
}
