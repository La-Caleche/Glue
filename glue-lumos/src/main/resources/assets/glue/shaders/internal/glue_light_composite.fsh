#version 150

// Final-color composite of linear Lumos illumination over the existing scene.

uniform sampler2D LightTex;     // unit 0: accumulated HDR light
uniform sampler2D SceneTex;     // unit 1: copy of the scene colour, taken pre-composite
uniform sampler2D MaterialAlbedo;
uniform sampler2D MaterialDepth;
uniform sampler2D SceneDepth;
uniform int HasMaterial;
uniform float Exposure;

in vec2 texCoord;
out vec4 fragColor;

float ditherNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec4 accumulated = texture(LightTex, texCoord);
    vec3 hdr = accumulated.rgb * Exposure;
    if (hdr == vec3(0.0)) discard;

    vec3 sceneSrgb = texture(SceneTex, texCoord).rgb;
    vec3 low = sceneSrgb / 12.92;
    vec3 high = pow((sceneSrgb + 0.055) / 1.055, vec3(2.4));
    vec3 sceneLinear = mix(low, high, step(vec3(0.04045), sceneSrgb));

    float sceneLuma = dot(sceneLinear, vec3(0.2126, 0.7152, 0.0722));
    float recoveredLuma;
    vec3 albedo;
    bool terrainMaterial = false;
    if (HasMaterial == 1) {
        float materialDepth = texture(MaterialDepth, texCoord).r;
        float sceneDepth = texture(SceneDepth, texCoord).r;
        terrainMaterial = materialDepth < 1.0 && abs(materialDepth - sceneDepth) < 1e-7;
    }
    if (terrainMaterial) {
        albedo = texture(MaterialAlbedo, texCoord).rgb;
        recoveredLuma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    } else {
        // Entities and translucent surfaces are not in the terrain buffer. Recover
        // their approximate material response from final scene colour as a fallback.
        recoveredLuma = mix(sceneLuma, sqrt(max(sceneLuma, 0.0)), 0.45);
        albedo = clamp(sceneLinear * (recoveredLuma / max(sceneLuma, 0.002)),
                       vec3(0.0), vec3(1.0));
    }

    // Real-world materials rarely absorb an entire colour channel. Keep a minimum
    // reflectance proportional to this texel's recovered luminance, so texture detail
    // remains intact. Transmitted light receives a stronger neutral component because
    // its hue already came from the stained-glass transmittance map.
    float transmitted = clamp(accumulated.a * Exposure, 0.0, 1.0);
    float minimumReflectance = mix(0.16, 0.5, transmitted);
    albedo = max(albedo, vec3(recoveredLuma * minimumReflectance));

    vec3 illumination = vec3(1.0) - exp(-hdr * albedo);
    vec3 finalLinear = sceneLinear + illumination * (vec3(1.0) - sceneLinear);

    vec3 encodedLow = finalLinear * 12.92;
    vec3 encodedHigh = 1.055 * pow(max(finalLinear, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    vec3 finalSrgb = mix(encodedLow, encodedHigh, step(vec3(0.0031308), finalLinear));

    // The main target is 8-bit. A static triangular-distribution dither turns broad
    // one-code-value contours into sub-pixel grain without temporal shimmer. Apply it
    // in display space, immediately before the hardware quantizes the shader output.
    float tpdf = ditherNoise(gl_FragCoord.xy)
               - ditherNoise(gl_FragCoord.xy + vec2(17.0, 59.0));
    finalSrgb = clamp(finalSrgb + vec3(tpdf / 255.0), vec3(0.0), vec3(1.0));
    fragColor = vec4(finalSrgb, 1.0);
}
