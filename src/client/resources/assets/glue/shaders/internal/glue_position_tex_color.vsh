#version 150

uniform mat4 MVP;

in vec3 Position;
in vec2 UV;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = MVP * vec4(Position, 1.0);
    texCoord = UV;
    vertexColor = Color;
}
