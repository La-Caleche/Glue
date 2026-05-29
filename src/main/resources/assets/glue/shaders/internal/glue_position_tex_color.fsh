#version 150

uniform sampler2D Sampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler, texCoord);
    vec4 color = texColor * vertexColor;
    if (color.a < 0.001) {
        discard;
    }
    fragColor = color;
}
