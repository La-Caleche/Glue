#version 150

uniform sampler2D DiffuseSamplerSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSamplerSampler, texCoord);

    // Luminance-weighted grayscale conversion
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Apply a subtle sepia tint for visual interest
    vec3 sepia = vec3(gray * 1.1, gray * 0.95, gray * 0.82);

    fragColor = vec4(sepia, color.a);
}
