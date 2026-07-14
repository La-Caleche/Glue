#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);

    // Luminance-weighted grayscale conversion (ITU-R BT.601)
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    fragColor = vec4(vec3(gray), color.a);
}
