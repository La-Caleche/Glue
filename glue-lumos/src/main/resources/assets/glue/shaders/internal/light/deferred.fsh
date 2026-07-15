#version 150

// Deferred light accumulation pass. Runs once per light, additively blended into
// an HDR lighting buffer. Normals are reconstructed from the scene depth buffer
// (the vanilla path has no normal G-buffer).

uniform sampler2D SceneDepth;   // unit 0: main render target depth
uniform sampler2D Gobo;         // unit 1: projected mask (GOBO only)
uniform sampler2D ShadowMap;    // unit 2: light-POV depth, OPAQUE casters only
uniform sampler2D ShadowTint;   // unit 3: light-POV transmittance (glass colour)
uniform int HasShadowTint;      // 1 if the transmittance + tint depth maps are bound
uniform sampler2D TintDepth;    // unit 6: light-POV depth, opaque PLUS translucent
uniform sampler2D MaterialAlbedo; // unit 7: linear terrain albedo + packed normal
uniform sampler2D MaterialDepth;  // unit 8: opaque terrain depth at capture time
uniform int HasMaterial;
uniform sampler2D GBufferAlbedo;  // unit 9: dynamic (entity) albedo + packed normal (A)
uniform sampler2D GBufferId;      // unit 10: dynamic material id (R) + owning depth24 (GBA)
uniform int HasGBuffer;           // 1 if the dynamic material G-buffer is bound

uniform mat4 InvViewProj;       // clip -> camera-relative world position
uniform mat4 ViewProj;          // camera-relative world -> clip (screen-space shadows)
uniform mat4 LightMatrix;       // world -> gobo clip (GOBO only)
uniform mat4 LightViewProj;     // camera-relative world -> shadow-map clip
uniform vec2 TexelSize;         // 1/width, 1/height

uniform float ShadowTexel;      // 1/shadowMapSize
uniform float ShadowNear;       // shadow frustum near plane
uniform float ShadowFar;        // shadow frustum far plane
uniform float ShadowFocalY;     // lightProj[1][1] -- converts world size <-> NDC size
uniform float LightSize;        // emitter radius, blocks. Drives penumbra width.
uniform int HasShadowMap;       // 1 if a real shadow map is bound
uniform int ShadowFace;         // cube face 0..5 for a point light, -1 for a spot

uniform vec3 LightPos;          // camera-relative
uniform vec3 LightColor;        // linear RGB, already scaled by intensity
uniform float Range;            // max reach, blocks
uniform int LightType;          // 0 = POINT, 1 = SPOT, 2 = GOBO
uniform vec3 SpotDir;           // normalized world direction (SPOT/GOBO)
uniform float CosInner;         // cos(inner half-angle)
uniform float CosOuter;         // cos(outer half-angle)
uniform int HasGobo;            // 1 if a gobo texture is bound

const int BLOCKER_TAPS = 8;
const int PCF_TAPS = 12;

// Coloured-shadow diffusion (a mini-PCSS over the with-translucents depth map).
const int TINT_SEARCH_TAPS = 6;
const int TINT_TAPS = 8;
// Sideways spread, in blocks per block travelled past the glass. Light scattering
// through a pane keeps diffusing after it exits, so the coloured pool's edge softens
// with distance behind the pane instead of staying a hard projected silhouette.
const float GLASS_DIFFUSION = 0.12;

// A lit pane behaves like a glossy TRANSPARENT surface, not a coloured emitter. Vanilla's
// translucent pass already draws the pane as absorption -- it tints and darkens whatever
// sits behind it (a shaderpack writes this as translucentMult = albedo, MULTIPLIED onto the
// background; it can only subtract, never add). So Lumos must not repaint the pane with its
// own albedo additively: that fights the vanilla render and floods the pane to a flat,
// saturated, opaque block. Lumos adds only the pane's own light RESPONSE:
//   GLASS_SCATTER   the coloured diffuse response of a pane FACING the light -- its "lit"
//                   brightness. A fraction of a full Lambert term (glass transmits most
//                   light, so a pane reads dimmer than an opaque block, but must still
//                   clearly read as lit, not dark).
//   GLASS_SPECULAR  a tight, light-COLOURED glint where the reflection lines up -- the
//                   highlight real glass catches, which reads as glossy-transparent rather
//                   than as a coloured fill (the specular/fresnel term shaderpacks use).
//   GLASS_FORWARD   forward-scatter transmission: a BACKLIT pane glows brightest where the
//                   eye looks straight through it at the light (real stained glass lit by
//                   the sun behind it), fading to the tint off-axis. This gradient is what
//                   turns a flat uniform fill into a lit pane -- the transmission analog of
//                   the specular glint, and the biggest anti-flatness win for backlit panes.
const float GLASS_SCATTER = 0.6;
const float GLASS_SPECULAR = 1.1;
const float GLASS_SHININESS = 40.0;
// Face-on reflectance floor. Physically glass reflects only ~4% head-on, which makes a
// point light's reflection invisible unless you catch it at a grazing angle -- so the only
// visible shine lands on the transmission (back-lit) side and the surface reads as if the
// reflection were reversed. Lift the floor so a pane facing the light still glints.
const float GLASS_FRESNEL_FLOOR = 0.22;
const float GLASS_FORWARD = 0.9;
const float GLASS_FORWARD_SHININESS = 9.0;

// Same opacity boost the shadow bake applies (glue_shadow_tint.fsh): vanilla stained
// glass is ~50% opaque, which tints far too weakly to read as coloured light. The glow
// and the projected pool must use the same value or the pane visibly disagrees with
// the light it casts.
const float TINT_STRENGTH = 1.8;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstruct(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 worldH = InvViewProj * clip;
    return worldH.xyz / worldH.w;
}

vec2 signNotZero(vec2 value) {
    return vec2(value.x >= 0.0 ? 1.0 : -1.0, value.y >= 0.0 ? 1.0 : -1.0);
}

vec3 unpackNormal(float packedNormal) {
    float packedValue = floor(packedNormal * 255.0 + 0.5);
    vec2 encoded = vec2(mod(packedValue, 16.0), floor(packedValue / 16.0)) / 15.0;
    vec2 octahedral = encoded * 2.0 - 1.0;
    vec3 normal = vec3(octahedral, 1.0 - abs(octahedral.x) - abs(octahedral.y));
    if (normal.z < 0.0) {
        normal.xy = (vec2(1.0) - abs(normal.yx)) * signNotZero(normal.xy);
    }
    return normalize(normal);
}

float unpackDepth24(vec3 encodedDepth) {
    vec3 depthBytes = floor(encodedDepth * 255.0 + 0.5);
    return dot(depthBytes, vec3(65536.0, 256.0, 1.0)) / 16777215.0;
}

// Golden-angle spiral: even areal coverage from few taps, which is what lets 18
// samples look smooth where a 3x3 box grid looks like a 3x3 box grid.
vec2 vogel(int i, int n, float phi) {
    float r = sqrt((float(i) + 0.5) / float(n));
    float theta = float(i) * 2.39996323 + phi;
    return vec2(cos(theta), sin(theta)) * r;
}

// Per-pixel rotation of the sample disk. Interleaved gradient noise, not a white-noise
// hash: it decorrelates neighbouring pixels just as well but its error is low-frequency,
// so a soft shadow edge comes out grainy rather than salt-and-pepper. Spatial only -- a
// frame-varying phase would shimmer, and there is no temporal filter here to resolve it.
float rotationPhi() {
    float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    return 6.2831853 * ign;
}

float linearizeShadow(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * ShadowNear * ShadowFar)
         / (ShadowFar + ShadowNear - z * (ShadowFar - ShadowNear));
}

// Which cube face a direction from the light falls on. The six faces are rendered
// with 90-degree frusta, so "dominant axis" and "inside that face's frustum" are the
// same test -- the faces tile the sphere with no gap and no double-counting.
int cubeFace(vec3 d) {
    vec3 a = abs(d);
    if (a.x >= a.y && a.x >= a.z) return d.x > 0.0 ? 0 : 1;
    if (a.y >= a.z)               return d.y > 0.0 ? 2 : 3;
    return d.z > 0.0 ? 4 : 5;
}

// Normal-offset bias: nudge the receiver off its surface, toward the light, BEFORE
// projecting. Removes acne without the peter-panning a pure depth bias causes (the
// shadow stays anchored to the caster's base). Takes and returns LIGHT-relative
// space -- the map is baked light-relative so it survives camera movement and can
// be cached. The caller must use the biased position for BOTH cube-face selection
// and sampling: pick the face from the unbiased point and the bias can nudge it out
// of that face's frustum, which shows up as bright lines along the face seams.
vec3 shadowBiasedPos(vec3 P, vec3 N, vec3 L, float distToLight, float ndotl) {
    float shadowRes = 1.0 / max(ShadowTexel, 1e-6);
    // World-space WIDTH of one shadow texel at this distance. Deriving the bias from it
    // means it holds at any shadow-map resolution.
    float texelWorld = distToLight / max(ShadowFocalY, 1e-4) * (2.0 / shadowRes);

    // ...but width is not what causes acne: the DEPTH a texel spans across the surface
    // grows as 1/cos(incidence). On a floor lit at a grazing angle -- exactly what an
    // eye-height point light does to the ground a few blocks away -- one texel covers a
    // huge depth range, and a bias sized for the width lets the floor shadow itself in
    // long streaks. Scale the offset with the slope.
    float slope = clamp(1.0 / max(ndotl, 0.1), 1.0, 8.0);
    return (P - LightPos) + (N + L) * max(0.02, 1.5 * texelWorld * slope);
}

// One PCF tap, compared THEN bilinearly blended.
//
// The obvious way round -- point-sample the depth, compare once -- makes every tap a
// hard yes/no, so a partially-shadowed pixel gets a coin flip and the result is
// salt-and-pepper. Comparing the four texels around the tap and interpolating the four
// binary answers gives a smooth partial occlusion per tap, which is worth roughly 4x the
// taps for a quarter of the noise. (Hardware does this for free via sampler2DShadow, but
// the blocker search below needs the raw depth from the same texture.)
float comparisonTap(sampler2D depthMap, vec2 uv, float cmp) {
    float res = 1.0 / max(ShadowTexel, 1e-6);
    vec2 t = uv * res - 0.5;
    vec2 f = fract(t);
    vec2 base = (floor(t) + 0.5) * ShadowTexel;

    float s00 = step(cmp, texture(depthMap, base).r);
    float s10 = step(cmp, texture(depthMap, base + vec2(ShadowTexel, 0.0)).r);
    float s01 = step(cmp, texture(depthMap, base + vec2(0.0, ShadowTexel)).r);
    float s11 = step(cmp, texture(depthMap, base + vec2(ShadowTexel, ShadowTexel)).r);

    return mix(mix(s00, s10, f.x), mix(s01, s11, f.x), f.y);
}

float shadowTap(vec2 uv, float cmp) {
    return comparisonTap(ShadowMap, uv, cmp);
}

// Bilinear blocker coverage plus its weighted linear depth. Unlike counting a tap as
// either blocked or clear, this changes continuously as the search disk crosses texels,
// preventing PCSS radius changes from appearing as contour bands.
void blockerTap(sampler2D depthMap, vec2 uv, float cmp,
                out float depthSum, out float blockerWeight) {
    float res = 1.0 / max(ShadowTexel, 1e-6);
    vec2 t = uv * res - 0.5;
    vec2 f = fract(t);
    vec2 base = (floor(t) + 0.5) * ShadowTexel;
    vec4 depths = vec4(
        texture(depthMap, base).r,
        texture(depthMap, base + vec2(ShadowTexel, 0.0)).r,
        texture(depthMap, base + vec2(0.0, ShadowTexel)).r,
        texture(depthMap, base + vec2(ShadowTexel, ShadowTexel)).r
    );
    vec4 weights = vec4(
        (1.0 - f.x) * (1.0 - f.y),
        f.x * (1.0 - f.y),
        (1.0 - f.x) * f.y,
        f.x * f.y
    );
    vec4 blocked = vec4(1.0) - step(vec4(cmp), depths);
    vec4 linearDepths = vec4(
        linearizeShadow(depths.x), linearizeShadow(depths.y),
        linearizeShadow(depths.z), linearizeShadow(depths.w)
    );
    blockerWeight = dot(weights, blocked);
    depthSum = dot(weights * blocked, linearDepths);
}

// TintDepth begins as an exact copy of ShadowMap, then nearer translucent blockers replace
// its opaque depth. Qualifying against both maps prevents opaque blockers from being mistaken
// for glass while preserving bilinear coverage at pane edges.
void tintBlockerTap(vec2 uv, float cmp, out float depthSum, out float blockerWeight) {
    float res = 1.0 / max(ShadowTexel, 1e-6);
    vec2 t = uv * res - 0.5;
    vec2 f = fract(t);
    vec2 base = (floor(t) + 0.5) * ShadowTexel;
    vec2 dx = vec2(ShadowTexel, 0.0);
    vec2 dy = vec2(0.0, ShadowTexel);
    vec4 tintDepths = vec4(
        texture(TintDepth, base).r,
        texture(TintDepth, base + dx).r,
        texture(TintDepth, base + dy).r,
        texture(TintDepth, base + dx + dy).r
    );
    vec4 opaqueDepths = vec4(
        texture(ShadowMap, base).r,
        texture(ShadowMap, base + dx).r,
        texture(ShadowMap, base + dy).r,
        texture(ShadowMap, base + dx + dy).r
    );
    vec4 weights = vec4(
        (1.0 - f.x) * (1.0 - f.y),
        f.x * (1.0 - f.y),
        (1.0 - f.x) * f.y,
        f.x * f.y
    );
    vec4 receiverBlocked = vec4(1.0) - step(vec4(cmp), tintDepths);
    vec4 translucentBlocker = step(vec4(0.5 / 16777215.0), opaqueDepths - tintDepths);
    vec4 blocked = receiverBlocked * translucentBlocker;
    vec4 linearDepths = vec4(
        linearizeShadow(tintDepths.x), linearizeShadow(tintDepths.y),
        linearizeShadow(tintDepths.z), linearizeShadow(tintDepths.w)
    );
    blockerWeight = dot(weights, blocked);
    depthSum = dot(weights * blocked, linearDepths);
}

// PCSS: search for blockers, estimate the penumbra from how far in front of the
// receiver they sit, then filter over exactly that width. Contact shadows come
// out sharp and distant ones soften, instead of everything being uniformly blurry.
// Pb is the light-relative, already-biased receiver. Returns 1 (lit) .. 0 (shadowed).
//
// Coloured shadows use the shaderpack dual-depth convention: ShadowMap holds OPAQUE
// casters only (glass must not cast black), TintDepth holds opaque PLUS translucent.
// A receiver shadowed in TintDepth but lit here has a pane -- and only a pane --
// between it and the light: behindPane is the filtered coverage (0..1, soft at the
// pool's edges) and tintRaw the filtered transmittance. Biased depth compares at full
// depth precision: no distances packed into 8-bit alpha (whose quantisation used to
// band the tint boundary into waves and start the tint on the pane itself).
float sampleShadowMap(vec3 Pb, float distToLight, out vec4 tintRaw, out float behindPane,
                      out float paneAhead) {
    tintRaw = vec4(1.0);
    behindPane = 0.0;
    paneAhead = 0.0;

    vec4 lc = LightViewProj * vec4(Pb, 1.0);
    if (lc.w <= 0.0) return 1.0;
    vec3 ndc = lc.xyz / lc.w;
    if (abs(ndc.z) > 1.0) return 1.0;   // beyond the map's depth range

    // A spot's map covers only its cone: outside it, there is nothing to occlude.
    // A cube face is guaranteed in range by the face test, up to float slop at the
    // seam -- so clamp there instead of rejecting, or the slop becomes a bright line.
    if (ShadowFace < 0 && any(greaterThan(abs(ndc.xy), vec2(1.0)))) return 1.0;

    vec2 uv = clamp(ndc.xy * 0.5 + 0.5, vec2(0.0), vec2(1.0));
    float refDepth = ndc.z * 0.5 + 0.5;

    float phi = rotationPhi();

    // Keep every tap inside the map. A tap that walks off the edge of a cube face
    // would otherwise wrap onto whatever the clamp mode hands back, which reads as a
    // hard line along the seam; clamping to the border texel continues the surface
    // approximately right instead.
    vec2 lo = vec2(0.5 * ShadowTexel);
    vec2 hi = vec2(1.0 - 0.5 * ShadowTexel);

    // Perspective-scaled depth bias: non-linear depth loses precision with
    // distance, so a constant bias is far too small near the far plane.
    float bias = 0.05 * ShadowFar * ShadowNear
               / max(distToLight * distToLight * (ShadowFar - ShadowNear), 1e-6);
    float cmp = refDepth - bias;

    float recvDist = linearizeShadow(refDepth);

    // World size -> shadow-map UV radius at this depth.
    float uvPerWorld = ShadowFocalY / max(recvDist, 0.1) * 0.5;
    float searchRadius = max(LightSize * uvPerWorld, 2.0 * ShadowTexel);

    // Has the light crossed glass on the way to this receiver? Shadowed in the
    // with-translucents map (while the PCSS below samples the opaque-only map) means
    // yes. The same bias that stops opaque acne keeps a pane from tinting ITSELF: its
    // own stored depth sits at its refDepth, just behind cmp. Runs before the fully-lit
    // early-out below, or a receiver whose only blocker is glass would lose its colour.
    //
    // Mini-PCSS for the tint: find the pane's distance, then filter the compare AND the
    // colour over a disk that widens with how far the receiver sits behind the pane --
    // light scattering through glass keeps diffusing after it exits, so the coloured
    // pool's edge softens with distance instead of staying a hard projected silhouette,
    // and overlapping projections of different panes cross-fade into each other.
    if (HasShadowTint == 1) {
        // Larger bias than the opaque compare: an obliquely-lit pane's NEIGHBOURING
        // texels (the disk taps) sit marginally closer to the light than the pixel's
        // own depth, and with the tight bias a pane partially "tints itself" in
        // irregular blotches. The extra bias only shifts where the tint starts by a
        // few centimetres behind the pane.
        float cmpTint = refDepth - 2.5 * bias;

        float tintSearch = max(searchRadius, 8.0 * ShadowTexel);
        float paneSum = 0.0;
        float paneWeight = 0.0;
        for (int i = 0; i < TINT_SEARCH_TAPS; i++) {
            vec2 t = clamp(uv + vogel(i, TINT_SEARCH_TAPS, phi + 2.5) * tintSearch, lo, hi);
            float sampleDepth;
            float sampleWeight;
            tintBlockerTap(t, cmpTint, sampleDepth, sampleWeight);
            paneSum += sampleDepth;
            paneWeight += sampleWeight;
        }
        if (paneWeight > 1e-4) {
            float paneDist = paneSum / paneWeight;
            float spreadWorld = max(recvDist - paneDist, 0.0)
                              * (GLASS_DIFFUSION + LightSize / max(paneDist, 0.5));
            float tintRadius = clamp(spreadWorld * uvPerWorld,
                                     1.5 * ShadowTexel, 24.0 * ShadowTexel);

            vec3 col = vec3(0.0);
            float frac = 0.0;
            for (int i = 0; i < TINT_TAPS; i++) {
                vec2 t = clamp(uv + vogel(i, TINT_TAPS, phi + 2.5) * tintRadius, lo, hi);
                float ignoredDepth;
                float coverage;
                tintBlockerTap(t, cmpTint, ignoredDepth, coverage);
                col += mix(vec3(1.0), texture(ShadowTint, t).rgb, coverage);
                frac += coverage;
            }
            behindPane = frac / float(TINT_TAPS);
            tintRaw = vec4(col / float(TINT_TAPS), 1.0);
            // How far in FRONT of the receiver the blocking pane really sits. When the
            // receiver is itself a pane, its own presence in the tint maps yields a
            // paneDist barely closer than recvDist -- this fades that self-term to
            // zero while a genuinely stacked pane (>= 1 block ahead) keeps full effect.
            paneAhead = smoothstep(0.25, 1.0, recvDist - paneDist);
        }
    }

    // 1) Blocker search.
    float blockerSum = 0.0;
    float blockerWeight = 0.0;
    for (int i = 0; i < BLOCKER_TAPS; i++) {
        vec2 t = clamp(uv + vogel(i, BLOCKER_TAPS, phi) * searchRadius, lo, hi);
        float sampleDepth;
        float sampleWeight;
        blockerTap(ShadowMap, t, cmp, sampleDepth, sampleWeight);
        blockerSum += sampleDepth;
        blockerWeight += sampleWeight;
    }
    if (blockerWeight <= 1e-4) return 1.0;   // fully lit, and we skip the PCF entirely
    float avgBlocker = blockerSum / blockerWeight;

    // 2) Penumbra width from the similar-triangles estimate.
    float penumbraWorld = (recvDist - avgBlocker) / max(avgBlocker, 0.05) * LightSize;
    // The upper clamp is a noise budget, not a style choice: spread the disk wider than
    // the tap count can cover and a "softer" shadow just becomes a grainier one.
    float pcfRadius = clamp(penumbraWorld * uvPerWorld,
                            1.5 * ShadowTexel, 14.0 * ShadowTexel);

    // 3) PCF over that width.
    float lit = 0.0;
    for (int i = 0; i < PCF_TAPS; i++) {
        lit += shadowTap(clamp(uv + vogel(i, PCF_TAPS, phi) * pcfRadius, lo, hi), cmp);
    }
    float shadow = lit / float(PCF_TAPS);

    // A spot's map ends at its frustum, and "outside = lit" against "inside =
    // shadowed" reads as a hard-edged quadrilateral on the floor -- so fade it out.
    // A cube face must NOT fade: its border is a seam with the next face, not an
    // edge of the shadowed region.
    if (ShadowFace < 0) {
        vec2 f = smoothstep(vec2(0.0), vec2(0.06), uv)
               * (1.0 - smoothstep(vec2(0.94), vec2(1.0), uv));
        shadow = mix(1.0, shadow, f.x * f.y);
    }
    return shadow;
}

void main() {
    float depth = texture(SceneDepth, texCoord).r;
    // Sky / no geometry: nothing to light. This also gives free depth occlusion --
    // we only ever shade the visible surface.
    if (depth >= 1.0) discard;

    vec3 P = reconstruct(texCoord, depth);

    // Cheap reject before the (much more expensive) normal reconstruction. The exact
    // cube-face test has to wait until the shadow bias is known -- see below.
    if (distance(P, LightPos) > Range) discard;

    bool terrainMaterial = false;
    vec4 material = vec4(0.0);
    if (HasMaterial == 1) {
        float materialDepth = texture(MaterialDepth, texCoord).r;
        terrainMaterial = materialDepth < 1.0 && abs(materialDepth - depth) < 1e-5;
        if (terrainMaterial) material = texture(MaterialAlbedo, texCoord);
    }

    // A captured surface (terrain id 1, entity id 2, particle id 3, glass id 4) that still owns
    // this pixel gets its real material data instead of the depth-derivative fallback, which reads
    // a silhouette as geometry. Ownership: the depth it wrote still resolves to the scene surface
    // (world-space, distance-scaled tolerance -- see composite.fsh) -- otherwise the id is stale.
    bool gbufferOwned = false;
    float gbufferId = 0.0;
    if (HasGBuffer == 1) {
        vec4 dynamicMaterial = texture(GBufferId, texCoord);
        gbufferId = dynamicMaterial.r * 255.0;
        float ownerDepth = unpackDepth24(dynamicMaterial.gba);
        vec3 ownerP = reconstruct(texCoord, ownerDepth);
        gbufferOwned = distance(ownerP, P) < 0.02 + 0.01 * length(P)
                && gbufferId > 0.5 && gbufferId < 4.5;
    }
    // Terrain (1) and entities (2) carry a real captured normal; particles (3, billboards) do not;
    // glass (4) carries albedo + opacity but derives its normal from depth (panes are axis-aligned),
    // so it takes the depth-derivative path below and is handled by the dedicated glass branch.
    bool gbufferSolid = gbufferOwned && gbufferId < 2.5;
    bool gbufferParticle = gbufferOwned && gbufferId > 2.5 && gbufferId < 3.5;
    bool gbufferGlass = gbufferOwned && gbufferId > 3.5;

    vec3 N;
    if (gbufferSolid) {
        N = unpackNormal(texture(GBufferAlbedo, texCoord).a);
    } else if (gbufferParticle) {
        // Billboards have no real surface normal; face the light so a mote simply catches any
        // nearby coloured light by proximity, rather than showing a bogus fixed-normal response.
        vec3 toLight = LightPos - P;
        float toLightLen = length(toLight);
        N = toLightLen > 1e-4 ? toLight / toLightLen : vec3(0.0, 1.0, 0.0);
    } else if (terrainMaterial) {
        N = unpackNormal(material.a);
    } else {
        // Fallback for entities and translucent surfaces: reconstruct an edge-aware
        // normal from neighbouring depth because they are not in the terrain buffer.
        float dr = texture(SceneDepth, texCoord + vec2(TexelSize.x, 0.0)).r;
        float dl = texture(SceneDepth, texCoord - vec2(TexelSize.x, 0.0)).r;
        float du = texture(SceneDepth, texCoord + vec2(0.0, TexelSize.y)).r;
        float dd = texture(SceneDepth, texCoord - vec2(0.0, TexelSize.y)).r;
        vec3 pr = dr < 1.0 ? reconstruct(texCoord + vec2(TexelSize.x, 0.0), dr) : P;
        vec3 pl = dl < 1.0 ? reconstruct(texCoord - vec2(TexelSize.x, 0.0), dl) : P;
        vec3 pu = du < 1.0 ? reconstruct(texCoord + vec2(0.0, TexelSize.y), du) : P;
        vec3 pd = dd < 1.0 ? reconstruct(texCoord - vec2(0.0, TexelSize.y), dd) : P;
        float er = dr < 1.0 ? dot(pr - P, pr - P) : 1e30;
        float el = dl < 1.0 ? dot(pl - P, pl - P) : 1e30;
        float eu = du < 1.0 ? dot(pu - P, pu - P) : 1e30;
        float ed = dd < 1.0 ? dot(pd - P, pd - P) : 1e30;
        vec3 ddxPos = er < el ? pr - P : P - pl;
        vec3 ddyPos = eu < ed ? pu - P : P - pd;
        float lx = dot(ddxPos, ddxPos);
        float ly = dot(ddyPos, ddyPos);
        if (lx < 1e-24 || ly < 1e-24) discard;
        vec3 cr = cross(ddxPos * inversesqrt(lx), ddyPos * inversesqrt(ly));
        float crl = dot(cr, cr);
        if (crl < 1e-8) discard;
        N = cr * inversesqrt(crl);
    }

    vec3 viewDir = normalize(-P);            // camera sits at the origin here
    if (dot(N, viewDir) < 0.0) N = -N;

    vec3 toLight = LightPos - P;
    float dist = length(toLight);
    if (dist > Range) discard;
    vec3 L = toLight / max(dist, 1e-4);

    // NOT clamped yet: a backlit pane of glass has a negative N.L and still has to glow.
    // The facing-away discard waits until we know whether this fragment IS the glass.
    float ndotl = dot(N, L);

    // Keep photometric falloff independent from Range. Normalizing the whole curve
    // by Range makes a long-range light a broad, nearly uniform colour wash.
    // Range only supplies the smooth finite-support window.
    float normalizedDistance = dist / Range;
    float distanceSq = normalizedDistance * normalizedDistance;
    float window = max(1.0 - distanceSq * distanceSq, 0.0);
    window *= window;
    float atten = window / (1.0 + 0.22 * dist * dist);

    float shape = 1.0;
    if (LightType >= 1) {
        float cosA = dot(-L, normalize(SpotDir));
        shape = smoothstep(CosOuter, CosInner, cosA);
        if (LightType == 2 && HasGobo == 1) {
            vec4 lp = LightMatrix * vec4(P, 1.0);
            if (lp.w > 0.0) {
                vec2 guv = (lp.xy / lp.w) * 0.5 + 0.5;
                if (guv.x >= 0.0 && guv.x <= 1.0 && guv.y >= 0.0 && guv.y <= 1.0) {
                    shape *= texture(Gobo, guv).r;
                } else {
                    shape = 0.0;
                }
            } else {
                shape = 0.0;
            }
        }
    }
    if (shape <= 0.0) discard;

    float shadow = 1.0;
    vec4 tintRaw = vec4(1.0);
    float behindPane = 0.0;
    float paneAhead = 0.0;
    if (HasShadowMap == 1) {
        // abs(), not max(,0): a backlit pane is a real receiver here. Clamping to 0
        // maxes the slope bias for every backlit fragment, and the biased position is
        // also what the TINT map is sampled with -- so backlit glass was reading its
        // transmittance several texels off its true position, right where pane
        // ownership changes.
        vec3 Pb = shadowBiasedPos(P, N, L, dist, abs(ndotl));

        // A point light is accumulated once per cube face; each pass owns the
        // fragments whose dominant axis from the light is its face, so the six tile
        // the sphere without shading anything twice. Select the face from the BIASED
        // position -- the same one we are about to sample with -- or the bias can
        // nudge a fragment out of the face it was assigned to, which reads as bright
        // lines running along every face seam.
        if (ShadowFace >= 0 && cubeFace(Pb) != ShadowFace) discard;

        shadow = sampleShadowMap(Pb, dist, tintRaw, behindPane, paneAhead);
    } else if (ShadowFace >= 0) {
        discard;   // a face pass with no map would double-count this fragment
    }

    // Is the visible surface at this pixel glass? Answered by MATERIAL ID: the panes were
    // re-rasterised into the shared G-buffer with the exact frame matrices and stamped id=4,
    // depth-tested so only the frontmost pane survives -- so a GLASS id here means the scene's
    // frontmost surface is a pane, on every face including the ray-grazing ones the old tint-texel
    // heuristic could not resolve. The captured albedo carries the pane's own colour (RGB) and
    // opacity (A).
    bool isGlass = gbufferGlass;
    vec4 glassAlbedo = vec4(0.0);
    if (isGlass) {
        glassAlbedo = texture(GBufferAlbedo, texCoord);
    }

    vec3 tint = vec3(1.0);
    float diffuse;
    float indirect = 0.0;
    float specular = 0.0;

    if (isGlass) {
        // The pixel IS glass. Vanilla already drew it as absorption over its background,
        // so add only the pane's light response, never a full-albedo flood (see the
        // GLASS_* constants). A faint coloured in-scatter from either side -- a backlit
        // pane doesn't go black -- weighted by the pane's own opacity so a near-clear
        // texel stays see-through, plus the forward-scatter hotspot below. Both are tinted
        // (transmitted light takes the glass colour) and shadowed via `direct`.
        // Opacity weight with a floor: a denser texel scatters more, but even the
        // translucent centre of a stained texel must still read as lit, so it never
        // collapses toward zero the way raw alpha does.
        float paneOpacity = 0.35 + 0.65 * glassAlbedo.a;
        float forward = pow(max(dot(-L, viewDir), 0.0), GLASS_FORWARD_SHININESS)
                      * GLASS_FORWARD * step(ndotl, 0.0);
        diffuse = abs(ndotl) * GLASS_SCATTER * paneOpacity + forward;
        tint = mix(vec3(1.0), glassAlbedo.rgb, clamp(glassAlbedo.a * TINT_STRENGTH, 0.0, 1.0));

        // Blinn-Phong glint, Schlick-Fresnel weighted (glass grazes bright) but with a
        // raised floor so it still reads face-on. Front-lit only: a backlit pane transmits
        // rather than reflects, so gate on ndotl > 0.
        // Guard the half-vector: L + viewDir is the zero vector when the light sits exactly
        // opposite the view ray (a backlit pane seen edge-on), and normalize(0) is a NaN. Fall
        // back to N and drop the specular there -- an undefined half-vector has no highlight.
        vec3 halfSum = L + viewDir;
        float halfLen = length(halfSum);
        vec3 halfVec = halfLen > 1e-6 ? halfSum / halfLen : N;
        float fresnel = GLASS_FRESNEL_FLOOR
                      + (1.0 - GLASS_FRESNEL_FLOOR) * pow(1.0 - max(dot(N, viewDir), 0.0), 5.0);
        specular = pow(max(dot(N, halfVec), 0.0), GLASS_SHININESS)
                 * fresnel * GLASS_SPECULAR * step(0.0, ndotl) * step(1e-6, halfLen);

        // Light that crossed ANOTHER pane before reaching this one arrives pre-tinted.
        // Only the colour is wanted, not that pane's texture pattern -- normalising by
        // the max channel keeps the channel ratios (hue, saturation) and drops the
        // luminance detail. Gated by paneAhead (the tint maps include this pane itself;
        // self-tint reads as coloured blotches printed on the face) and weighted by
        // coverage, so a faint contaminated fringe stays faint instead of being
        // amplified to full strength by the normalisation.
        float crossStrength = paneAhead * behindPane;
        if (crossStrength > 0.0) {
            float tintMax = max(tintRaw.r, max(tintRaw.g, tintRaw.b));
            if (tintMax >= 0.05) {
                tint *= mix(vec3(1.0), tintRaw.rgb * (0.9 / tintMax), crossStrength);
            }
        }
    } else {
        // Keep Lambertian direct light exact. The indirect term approximates the
        // low-frequency fill that even a small skylight opening contributes in vanilla:
        // without it, a sealed room collapses directly from N.L into black and every
        // geometric crease is perceptually exaggerated.
        if (ndotl <= -0.08) discard;
        diffuse = max(ndotl, 0.0);
        indirect = 0.09 * smoothstep(-0.08, 0.0, ndotl)
                 + 0.025 * sqrt(max(ndotl, 0.0));
        // Tinted when the with-translucents depth map says a pane sits between this
        // receiver and the light. tintRaw was filtered against white over the diffusion
        // disk, so the pool's edge fades out instead of cutting off.
        if (behindPane > 0.0) {
            tint = tintRaw.rgb;
        }
    }

    float direct = diffuse * shadow;
    // The indirect term is a low-frequency ambient fill. Modulating it by the stochastic
    // per-pixel shadow injects grain that dominates on near-unlit faces, where the fill is
    // the ONLY light and its contrast against black is maximal. Fade the shadow modulation
    // out as the surface turns away from the light: lit faces keep contact darkening,
    // backfaces get a smooth fill instead of speckle.
    float bounceShadow = mix(1.0, mix(0.3, 1.0, shadow), smoothstep(0.0, 0.2, ndotl));
    float bounced = indirect * bounceShadow;
    float lightLuma = dot(LightColor, vec3(0.2126, 0.7152, 0.0722));
    vec3 bouncedColor = mix(LightColor, vec3(lightLuma), 0.35);
    // The glass glint is light-coloured (a reflection, not a transmission), so it is added
    // OUTSIDE the pane tint -- tinting it would turn the highlight into another colour wash.
    vec3 result = (tint * (LightColor * direct + bouncedColor * bounced)
                   + LightColor * specular * shadow) * atten * shape;

    // Alpha is metadata for the final composite, not opacity: it flags COLOURED transmitted
    // light -- the pool a pane casts on a surface behind it -- so the composite keeps that
    // pool's stained-glass hue. The pane itself is deliberately NOT flagged: pinning it to
    // the transmitted reflectance floor forced a uniform 0.5 albedo that washed out
    // vanilla's own textured, semi-transparent render of the pane and left a flat colour
    // slab. Unflagged, the pane keeps the ~0.16 floor, vanilla's texture and transparency
    // survive underneath, and Lumos lifts that render instead of repainting over it.
    float transmitted = behindPane;
    // The per-light glass math (half-vector normalize, tint normalisation) can produce a NaN at
    // degenerate geometry -- notably the edge-on seam between two panes where a light sits nearly
    // opposite the view ray. A NaN added into the additive HDR light buffer poisons the pixel, and
    // the combine pass turns any NaN into a black block. Drop the contribution instead: the pixel
    // keeps the light from every other source and its scene colour rather than going black.
    vec4 outColor = vec4(result, transmitted * (direct + bounced) * atten * shape);
    if (any(isnan(outColor)) || any(isinf(outColor))) discard;
    fragColor = outColor;
}
