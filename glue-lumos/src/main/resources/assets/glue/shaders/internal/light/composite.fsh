#version 150

// Linear composite of Lumos illumination over the scene, written to the HDR lit buffer.
// Bloom and tonemapping happen afterwards in the combine pass, which reads this buffer --
// so this pass writes EVERY pixel (not just lit ones) in linear light, and never clips or
// encodes to sRGB. Blooming the visible brightness this buffer holds (rather than the raw
// light field) is what makes the glow track actual bright pixels instead of nearby light.

uniform sampler2D LightTex;     // unit 0: accumulated HDR light
uniform sampler2D SceneTex;     // unit 1: copy of the scene colour, taken pre-composite
uniform sampler2D MaterialAlbedo;
uniform sampler2D MaterialDepth;
uniform sampler2D SceneDepth;
uniform int HasMaterial;
uniform float Exposure;

// Assumed reflectance when no material buffer covers this pixel. Mid-grey: the value a
// surface of unknown albedo is least wrong at. Keeps the fallback in the same range as a
// real MaterialAlbedo sample, so one Exposure serves both paths.
const float FALLBACK_REFLECTANCE = 0.5;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 accumulated = texture(LightTex, texCoord);
    vec3 hdr = accumulated.rgb * Exposure;

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
        terrainMaterial = materialDepth < 1.0 && abs(materialDepth - sceneDepth) < 1e-5;
    }
    if (terrainMaterial) {
        albedo = texture(MaterialAlbedo, texCoord).rgb;
        recoveredLuma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    } else {
        // Entities and translucent surfaces are not in the terrain buffer, and reflectance
        // cannot be separated from illumination in a single already-lit sample -- they are
        // the same product. Estimating it from scene brightness works while the scene IS
        // lit, but collapses as the scene goes dark: a white wall in an unlit room used to
        // recover an albedo near 0.05, which is what forced a ~6x compensating global
        // exposure and let the vanilla lightmap's colour leak into every dynamic light
        // (a white lamp rendered blue under a skylight, yellow in a sealed room).
        //
        // So fade the estimate toward mid-grey exactly as fast as it loses support. A lit
        // pixel keeps the recovered value; an unlit one falls back to the reflectance an
        // unknown surface is least wrong at, instead of to zero.
        float support = smoothstep(0.0, 0.05, sceneLuma);
        float recovered = mix(sceneLuma, sqrt(max(sceneLuma, 0.0)), 0.45);
        float reflectance = mix(FALLBACK_REFLECTANCE, clamp(recovered, 0.05, 1.0), support);

        // Hue ratios only, pulled halfway to neutral: part of the scene's hue belongs to
        // the vanilla lightmap rather than the surface, and there is no way to tell which.
        vec3 chroma = sceneLuma > 1e-4 ? sceneLinear / sceneLuma : vec3(1.0);
        albedo = clamp(mix(vec3(1.0), chroma, 0.5) * reflectance, vec3(0.0), vec3(1.0));
        recoveredLuma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
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

    // Linear HDR out. The combine pass adds bloom and tonemaps this to the display.
    fragColor = vec4(finalLinear, 1.0);
}
