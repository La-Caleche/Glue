#version 150

uniform sampler2D DiffuseSamplerSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Get texel size from texture dimensions
    vec2 texelSize = 1.0 / vec2(textureSize(DiffuseSamplerSampler, 0));

    // 5x5 box blur kernel
    vec4 color = vec4(0.0);
    int samples = 0;

    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            color += texture(DiffuseSamplerSampler, texCoord + vec2(float(x), float(y)) * texelSize);
            samples++;
        }
    }

    fragColor = color / float(samples);
}
