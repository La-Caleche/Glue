#version 150

// Kept in sync with Minecraft's position_tex_color fragment interface, except a complete world
// render is presentation data rather than alpha coverage and must retain alpha-zero RGB texels.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    fragColor = vec4((color * ColorModulator).rgb, 1.0);
}
