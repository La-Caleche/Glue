#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Strength is a normalized [0..1] envelope, so the aberration's physical size belongs here: the
// data-driven curves a TimedEffectDefinition can name are all normalized, and a chain fed 1.0
// straight into the UV offset would sample most of the screen away.
layout(std140) uniform ChromaticConfig {
    float Strength;
};

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.14159265
#define MAX_OFFSET 0.05

mat2 rotationMatrix(float angle) {
    return mat2(cos(angle), -sin(angle),
                sin(angle),  cos(angle));
}

void main() {
    vec2 offsetFromCenter = texCoord - vec2(0.5);

    float dist = clamp(length(offsetFromCenter), 0.0, 1.0);
    dist = smoothstep(0.0, 1.0, dist);

    vec2 baseOffset = vec2(0.0, dist * Strength * MAX_OFFSET);

    mat2 rotRed  = rotationMatrix(PI / 3.0);
    mat2 rotBlue = rotationMatrix(-PI / 3.0);

    vec2 offsetRed  = rotRed  * baseOffset;
    vec2 offsetBlue = rotBlue * baseOffset;

    vec4 main_color = texture(InSampler, texCoord);
    float red   = texture(InSampler, texCoord + offsetRed).r;
    float green = main_color.g;
    float blue  = texture(InSampler, texCoord + offsetBlue).b;

    fragColor = vec4(red, green, blue, main_color.a);
}
