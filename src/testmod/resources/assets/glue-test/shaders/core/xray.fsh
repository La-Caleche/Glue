#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    color *= vertexColor * ColorModulator;
    if (color.a < ALPHA_CUTOUT) discard;

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    vec2 uvDeriv = fwidth(texCoord0) * 50.0;
    float edgeX = smoothstep(0.0, uvDeriv.x, fract(texCoord0.x * 16.0));
    float edgeY = smoothstep(0.0, uvDeriv.y, fract(texCoord0.y * 16.0));
    edgeX = min(edgeX, smoothstep(0.0, uvDeriv.x, 1.0 - fract(texCoord0.x * 16.0)));
    edgeY = min(edgeY, smoothstep(0.0, uvDeriv.y, 1.0 - fract(texCoord0.y * 16.0)));
    float edge = 1.0 - min(edgeX, edgeY);

    vec3 neonGreen = vec3(0.0, 1.0, 0.3);
    color.rgb = mix(color.rgb * 0.15, neonGreen, edge);
    color.a = mix(0.3, 1.0, edge);

    float pulse = sin(worldPos.y * 10.0 + worldPos.x * 5.0) * 0.5 + 0.5;
    color.rgb *= 0.8 + 0.4 * pulse * edge;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
