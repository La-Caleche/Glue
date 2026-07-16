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

// Inverse view-projection, used to reconstruct world positions for the dynamic-material
// ownership test below.
uniform mat4 InvViewProj;

// Dynamic material G-buffer: supported entity surfaces are captured in the same draw as the
// scene. GBufferId carries the material class plus owning depth; GBufferAlbedo is linear albedo
// (RGB) + packed normal (A). The normal is reserved for the deferred pass; this composite uses
// captured albedo so a grey entity stays grey.
uniform sampler2D GBufferAlbedo;
uniform sampler2D GBufferId;
uniform int HasGBuffer;

// Assumed reflectance when no material buffer covers this pixel. Mid-grey: the value a
// surface of unknown albedo is least wrong at. Keeps the fallback in the same range as a
// real MaterialAlbedo sample, so one Exposure serves both paths.
const float FALLBACK_REFLECTANCE = 0.5;

// Entities and particles are not in the terrain material buffer, so their albedo and
// normal are both guessed. Full additive light on that guess floods a pale mob (a grey
// wither) to white and paints a dark one a solid colour. We can't light them correctly, so
// we bound how far Lumos may move them: a nearby coloured light lends a subtle tint and
// never a flood. A subtle wrong overlay beats a blown-out one. Set to 0 to opt entities out
// of Lumos entirely (they keep their vanilla look untouched).
// Interim: entities/particles have no captured albedo or normal, and every attempt to light
// them from a single flat guess either floods a pale mob to white or leaves a dark one black
// (the cap could not serve both at once). Until they get their own material buffer -- real
// albedo + normal, the same data terrain already has -- they receive NO Lumos light and keep
// their untouched vanilla look, which is strictly better than a wrong overlay. 0 disables the
// entity path entirely; raise it only for a deliberate flat tint.
const float ENTITY_LIGHT_CAP = 0.0;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstruct(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 worldH = InvViewProj * clip;
    return worldH.xyz / worldH.w;
}

float unpackDepth24(vec3 encodedDepth) {
    vec3 depthBytes = floor(encodedDepth * 255.0 + 0.5);
    return dot(depthBytes, vec3(65536.0, 256.0, 1.0)) / 16777215.0;
}

// True when the frontmost surface at uv is a glass pane. The pane carries the GLASS material id
// (4), but the glass re-render is sorted only against other panes, so a pane behind an opaque wall
// stamped its id here too -- verify OWNERSHIP the same way the entity path does: the depth the pane
// wrote must still resolve to the scene surface, or it is occluded and this pixel is not glass.
bool frontmostIsGlass(vec2 uv, float sceneDepth) {
    if (HasGBuffer != 1) return false;
    vec4 idSample = texture(GBufferId, uv);
    float id = idSample.r * 255.0;
    if (id < 3.5 || id > 4.5) return false;
    float ownerDepth = unpackDepth24(idSample.gba);
    vec3 ownerP = reconstruct(uv, ownerDepth);
    vec3 Ps = reconstruct(uv, sceneDepth);
    return distance(ownerP, Ps) < 0.02 + 0.01 * length(Ps);
}

// Same ownership test for water (id 5). Like glass, water gets a real purpose-built deferred
// response, so it must be exempt from the uncaptured-translucent light cap below.
bool frontmostIsWater(vec2 uv, float sceneDepth) {
    if (HasGBuffer != 1) return false;
    vec4 idSample = texture(GBufferId, uv);
    float id = idSample.r * 255.0;
    if (id < 4.5 || id > 5.5) return false;
    float ownerDepth = unpackDepth24(idSample.gba);
    vec3 ownerP = reconstruct(uv, ownerDepth);
    vec3 Ps = reconstruct(uv, sceneDepth);
    return distance(ownerP, Ps) < 0.02 + 0.01 * length(Ps);
}

// Same ownership test for metal (id 6). Metal gets a real metallic deferred response, so it too must
// be exempt from the uncaptured-translucent light cap.
bool frontmostIsMetal(vec2 uv, float sceneDepth) {
    if (HasGBuffer != 1) return false;
    vec4 idSample = texture(GBufferId, uv);
    float id = idSample.r * 255.0;
    if (id < 5.5) return false;
    float ownerDepth = unpackDepth24(idSample.gba);
    vec3 ownerP = reconstruct(uv, ownerDepth);
    vec3 Ps = reconstruct(uv, sceneDepth);
    return distance(ownerP, Ps) < 0.02 + 0.01 * length(Ps);
}

void main() {
    vec4 accumulated = texture(LightTex, texCoord);
    vec3 hdr = accumulated.rgb * Exposure;

    vec3 sceneSrgb = texture(SceneTex, texCoord).rgb;
    vec3 low = sceneSrgb / 12.92;
    vec3 high = pow((sceneSrgb + 0.055) / 1.055, vec3(2.4));
    vec3 sceneLinear = mix(low, high, step(vec3(0.04045), sceneSrgb));

    float sceneLuma = dot(sceneLinear, vec3(0.2126, 0.7152, 0.0722));
    float sceneDepth = texture(SceneDepth, texCoord).r;
    bool isGlass = frontmostIsGlass(texCoord, sceneDepth);
    bool isWater = frontmostIsWater(texCoord, sceneDepth);
    bool isMetal = frontmostIsMetal(texCoord, sceneDepth);

    float recoveredLuma;
    vec3 albedo;
    bool terrainMaterial = false;
    if (HasMaterial == 1) {
        float materialDepth = texture(MaterialDepth, texCoord).r;
        terrainMaterial = materialDepth < 1.0 && abs(materialDepth - sceneDepth) < 1e-5;
    }
    // A captured material pixel (terrain id 1, entity id 2, particle id 3) carries its real
    // albedo -- use it directly and never fall to the guess-or-cap path.
    bool gbufferDynamic = false;
    if (HasGBuffer == 1) {
        vec4 dynamicMaterial = texture(GBufferId, texCoord);
        float id = dynamicMaterial.r * 255.0;
        // It owns this pixel only if the depth it wrote still resolves to the scene surface --
        // otherwise something opaque or translucent (a pane) took over in front and the id is
        // stale. Compare in world space with a distance-scaled tolerance (the same form the glass
        // test uses): a fixed window-depth epsilon is nonlinear -- far too loose far from the
        // camera, so it would match a distant surface behind the entity.
        float ownerDepth = unpackDepth24(dynamicMaterial.gba);
        vec3 ownerP = reconstruct(texCoord, ownerDepth);
        vec3 sceneP = reconstruct(texCoord, sceneDepth);
        bool ownsPixel = distance(ownerP, sceneP) < 0.02 + 0.01 * length(sceneP);
        gbufferDynamic = ownsPixel && id > 0.5 && id < 3.5;
    }

    if (gbufferDynamic) {
        albedo = texture(GBufferAlbedo, texCoord).rgb;
        recoveredLuma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    } else if (terrainMaterial) {
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

    // Cap the uncaptured overlay (see ENTITY_LIGHT_CAP) -- but NOT glass, which the deferred pass
    // gives a real, purpose-built response (scatter/specular/transmission) that clamping would
    // undo, and NOT captured surfaces, which carry real albedo and so do not flood. What stays
    // capped is genuinely uncaptured geometry (translucents with no material data). The guard is
    // "we have material capability at all" -- terrain buffer (vanilla) OR the G-buffer (Sodium).
    if ((HasMaterial == 1 || HasGBuffer == 1)
            && !terrainMaterial && !isGlass && !isWater && !isMetal && !gbufferDynamic) {
        float mag = max(illumination.r, max(illumination.g, illumination.b));
        if (mag > ENTITY_LIGHT_CAP) illumination *= ENTITY_LIGHT_CAP / mag;
    }

    vec3 finalLinear = sceneLinear + illumination * (vec3(1.0) - sceneLinear);
    // Catch-all: a NaN/Inf from anywhere in the light math would reach the combine pass, which
    // turns any NaN into a black block. Fall back to the unlit scene colour so a bad pixel shows
    // vanilla, never a black square.
    if (any(isnan(finalLinear)) || any(isinf(finalLinear))) finalLinear = sceneLinear;

    // Linear HDR out. The combine pass adds bloom and tonemaps this to the display.
    fragColor = vec4(finalLinear, 1.0);
}
