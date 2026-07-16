#version 150

// Screen-space reflection for glass AND water. Runs AFTER the light composite: for every pixel whose
// frontmost surface is a reflective material (glass pane id 4, water id 5), reflect the view ray about
// the surface normal, march it through the scene depth buffer, and blend the (already lit) scene colour
// it hits onto the surface -- a real environment reflection, Fresnel-weighted so it grazes bright. The
// point light itself is never in the scene colour (Lumos lights are not geometry), so this complements
// the specular glint from the deferred pass rather than replacing it.
//
// Glass derives an axis-snapped planar normal from depth; water uses an animated ripple normal so its
// reflection shimmers and breaks up like a real surface.

uniform sampler2D SceneColor;   // unit 0: composited, lit scene colour (sRGB-encoded)
uniform sampler2D SceneDepth;   // unit 1: scene depth
uniform sampler2D GBufferId;    // unit 2: material id (R); glass = 4, water = 5, metal = 6
uniform sampler2D GBufferAlbedo;// unit 3: linear albedo (RGB) -- tints a metal's reflection
uniform int HasGBuffer;         // 1 if the material G-buffer is bound

uniform mat4 InvViewProj;       // clip -> camera-relative world
uniform mat4 ViewProj;          // camera-relative world -> clip
uniform vec2 TexelSize;         // 1/width, 1/height
uniform float Strength;         // overall reflection intensity
uniform float Time;             // seconds, for water ripple animation
uniform vec3 CameraPos;         // camera world position (to anchor ripples in world space)

in vec2 texCoord;
out vec4 fragColor;

const int MARCH_STEPS = 28;
const int REFINE_STEPS = 6;
const float MAX_DISTANCE = 20.0;   // blocks the ray may travel
const float THICKNESS = 0.75;      // blocks; largest depth gap still counted as a hit
const float GLASS_R0 = 0.05;       // glass reflectance at normal incidence
const float WATER_R0 = 0.02;       // water reflectance at normal incidence

vec3 reconstruct(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = InvViewProj * clip;
    return world.xyz / world.w;
}

float unpackDepth24(vec3 encodedDepth) {
    vec3 depthBytes = floor(encodedDepth * 255.0 + 0.5);
    return dot(depthBytes, vec3(65536.0, 256.0, 1.0)) / 16777215.0;
}

// The reflective material id (4 glass, 5 water) that actually OWNS the pixel, or 0. The re-render is
// sorted only against its own kind, so a surface behind an opaque wall stamped its id here too; the
// depth it wrote must still resolve to the scene surface, or it is occluded and must not reflect.
float surfaceId(vec2 uv) {
    vec4 idSample = texture(GBufferId, uv);
    float id = idSample.r * 255.0;
    if (id < 3.5 || id > 6.5) return 0.0;
    float ownerDepth = unpackDepth24(idSample.gba);
    float sceneDepth = texture(SceneDepth, uv).r;
    vec3 ownerP = reconstruct(uv, ownerDepth);
    vec3 Ps = reconstruct(uv, sceneDepth);
    return distance(ownerP, Ps) < 0.02 + 0.01 * length(Ps) ? id : 0.0;
}

bool isReflectiveAt(vec2 uv) {
    return surfaceId(uv) > 0.5;
}

// Axis-snapped pane normal from the scene depth of the neighbouring reflective texels. Glass is
// overwhelmingly axis-aligned, so a near-axis derivative snaps to its exact axis (a clean, planar
// reflection); a genuinely diagonal pane keeps the derived value. Neighbours that are NOT reflective
// are dropped in favour of the centre, so the estimate stays valid at surface edges.
vec3 glassNormal(vec2 uv, vec3 P) {
    vec2 rx = uv + vec2(TexelSize.x, 0.0);
    vec2 lx = uv - vec2(TexelSize.x, 0.0);
    vec2 uy = uv + vec2(0.0, TexelSize.y);
    vec2 dy = uv - vec2(0.0, TexelSize.y);
    bool rGlass = isReflectiveAt(rx);
    bool lGlass = isReflectiveAt(lx);
    bool uGlass = isReflectiveAt(uy);
    bool dGlass = isReflectiveAt(dy);

    vec3 pr = rGlass ? reconstruct(rx, texture(SceneDepth, rx).r) : P;
    vec3 pl = lGlass ? reconstruct(lx, texture(SceneDepth, lx).r) : P;
    vec3 pu = uGlass ? reconstruct(uy, texture(SceneDepth, uy).r) : P;
    vec3 pd = dGlass ? reconstruct(dy, texture(SceneDepth, dy).r) : P;

    vec3 ddx = rGlass ? pr - P : P - pl;
    vec3 ddy = uGlass ? pu - P : P - pd;
    if (dot(ddx, ddx) < 1e-12 || dot(ddy, ddy) < 1e-12) return vec3(0.0);

    vec3 n = normalize(cross(ddx, ddy));
    vec3 a = abs(n);
    float m = max(a.x, max(a.y, a.z));
    if (m > 0.9) {
        n = (a.x >= a.y && a.x >= a.z) ? vec3(sign(n.x), 0.0, 0.0)
          : (a.y >= a.z)               ? vec3(0.0, sign(n.y), 0.0)
                                       : vec3(0.0, 0.0, sign(n.z));
    }
    return n;
}

// Animated ripple normal for a flat (top-facing) water surface, anchored in world XZ so the ripples
// stay put as the camera moves. A few directional wave layers summed into a height gradient; the
// amplitudes are small so the surface reads as gentle ripples rather than choppy waves.
vec3 waterNormal(vec2 worldXZ, float time) {
    vec2 grad = vec2(0.0);
    vec2 d1 = normalize(vec2(0.8, 0.6));  float f1 = 2.2;
    grad += d1 * f1 * cos(dot(worldXZ, d1) * f1 + time * 1.5) * 0.030;
    vec2 d2 = normalize(vec2(-0.5, 0.9)); float f2 = 3.9;
    grad += d2 * f2 * cos(dot(worldXZ, d2) * f2 + time * 2.3) * 0.020;
    vec2 d3 = normalize(vec2(0.3, -0.95)); float f3 = 6.1;
    grad += d3 * f3 * cos(dot(worldXZ, d3) * f3 + time * 3.1) * 0.008;
    return normalize(vec3(-grad.x, 1.0, -grad.y));
}

// Radial distance from the camera (which sits at the origin in this space). Two points that
// project to the same pixel lie on the same view ray, so comparing their radial distance is
// a valid "which is in front" test -- no separate view matrix needed.
float camDist(vec3 p) {
    return length(p);
}

void main() {
    if (HasGBuffer != 1) discard;
    float sceneDepth = texture(SceneDepth, texCoord).r;
    if (sceneDepth >= 1.0) discard;               // sky
    float id = surfaceId(texCoord);
    if (id < 0.5) discard;
    bool water = id > 4.5 && id < 5.5;
    bool metal = id > 5.5;

    vec3 P = reconstruct(texCoord, sceneDepth);
    // Water ripples; glass and metal are blocky, so they take the axis-snapped depth normal.
    vec3 N = water ? waterNormal((P + CameraPos).xz, Time) : glassNormal(texCoord, P);
    if (N == vec3(0.0)) discard;
    vec3 V = normalize(P);                         // camera -> fragment (incident ray)
    if (dot(N, V) > 0.0) N = -N;                    // face the surface toward the camera
    vec3 R = reflect(V, N);

    vec3 origin = P + N * 0.03;                     // lift off the surface to avoid self-hit
    float stepLen = MAX_DISTANCE / float(MARCH_STEPS);

    vec3 prev = origin;
    float prevDelta = -1.0;
    bool hit = false;
    vec2 hitUV = vec2(0.0);

    for (int i = 1; i <= MARCH_STEPS; i++) {
        vec3 sp = origin + R * (stepLen * float(i));
        vec4 clip = ViewProj * vec4(sp, 1.0);
        if (clip.w <= 0.0) break;                   // behind the camera
        vec2 uv = (clip.xy / clip.w) * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;

        float sd = texture(SceneDepth, uv).r;
        float delta = sd >= 1.0 ? -1.0
                    : camDist(sp) - camDist(reconstruct(uv, sd));

        // Front-to-behind crossing within the thickness window is a hit.
        if (prevDelta <= 0.0 && delta > 0.0 && delta < THICKNESS) {
            vec3 lo = prev;
            vec3 hi = sp;
            for (int r = 0; r < REFINE_STEPS; r++) {
                vec3 mid = 0.5 * (lo + hi);
                vec4 c = ViewProj * vec4(mid, 1.0);
                if (c.w <= 0.0) { lo = mid; continue; }   // behind camera: never divide by w <= 0
                vec2 muv = (c.xy / c.w) * 0.5 + 0.5;
                float msd = texture(SceneDepth, muv).r;
                float md = msd >= 1.0 ? -1.0
                         : camDist(mid) - camDist(reconstruct(muv, msd));
                if (md > 0.0) { hi = mid; hitUV = muv; } else { lo = mid; }
            }
            hit = true;
            break;
        }
        prev = sp;
        prevDelta = delta;
    }

    if (!hit) discard;
    if (any(lessThan(hitUV, vec2(0.0))) || any(greaterThan(hitUV, vec2(1.0)))) discard;

    // Fade at the screen edges: a ray that resolves near the border is sampling colour that
    // is about to leave the frame, and popping it in hard looks worse than letting it go.
    vec2 e = smoothstep(vec2(0.0), vec2(0.08), hitUV)
           * (vec2(1.0) - smoothstep(vec2(0.92), vec2(1.0), hitUV));
    float edge = e.x * e.y;

    // Metal reflects noticeably but must NOT become a mirror, or it washes the block's texture out to
    // a milky slab; water reflects more at grazing with a low face-on reflectance; glass least of all.
    float r0 = metal ? 0.2 : (water ? WATER_R0 : GLASS_R0);
    float strength = metal ? Strength * 0.7 : (water ? Strength * 1.4 : Strength);
    float fresnel = r0 + (1.0 - r0) * pow(1.0 - max(dot(N, -V), 0.0), 5.0);
    float alpha = clamp(fresnel * strength * edge, 0.0, 1.0);
    if (alpha <= 0.0) discard;

    vec3 reflected = texture(SceneColor, hitUV).rgb;
    // A metal's reflection takes on the metal's own colour (a gold block reflects gold), so tint the
    // reflected scene by the captured albedo.
    if (metal) reflected *= texture(GBufferAlbedo, texCoord).rgb;
    // A NaN/Inf sample renders as a solid black block and blends it straight onto the surface.
    // Drop the fragment rather than leak it; a missing reflection is invisible, a black hole isn't.
    if (any(isnan(reflected)) || any(isinf(reflected))) discard;
    fragColor = vec4(max(reflected, vec3(0.0)), alpha);
}
