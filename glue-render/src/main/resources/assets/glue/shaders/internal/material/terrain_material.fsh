#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 vertexNormal;

out vec4 fragColor;

vec3 srgbToLinear(vec3 color) {
    vec3 low = color / 12.92;
    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), color));
}

vec2 signNotZero(vec2 value) {
    return vec2(value.x >= 0.0 ? 1.0 : -1.0, value.y >= 0.0 ? 1.0 : -1.0);
}

float packNormal(vec3 normal) {
    normal /= abs(normal.x) + abs(normal.y) + abs(normal.z);
    vec2 octahedral = normal.xy;
    if (normal.z < 0.0) {
        octahedral = (vec2(1.0) - abs(octahedral.yx)) * signNotZero(octahedral);
    }
    vec2 encoded = floor((octahedral * 0.5 + 0.5) * 15.0 + 0.5);
    return (encoded.x + encoded.y * 16.0) / 255.0;
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    vec4 color = texel * vertexColor;
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    // Vanilla bakes a scalar AO/face-shade coefficient into vertex RGB. Divide out
    // that common magnitude while retaining biome/model tint ratios. Sodium's native
    // path can preserve the two terms exactly; this is the best recoverable signal
    // from vanilla's already-compiled vertex format.
    float shade = max(vertexColor.r, max(vertexColor.g, vertexColor.b));
    vec3 tint = shade > 1e-4 ? vertexColor.rgb / shade : vec3(1.0);
    fragColor = vec4(srgbToLinear(texel.rgb * tint), packNormal(normalize(vertexNormal)));
}
