#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
#ifdef SOURCE_BGRA
    color = color.bgra;
#endif
    vec4 tint = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb * tint.rgb * tint.a, color.a * tint.a);
}
