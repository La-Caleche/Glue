#version 150

// Screen-space reflection for glass. Runs AFTER the light composite: for every pixel whose
// frontmost surface is a glass pane, reflect the view ray about the pane normal, march it
// through the scene depth buffer, and blend the (already lit) scene colour it hits onto the
// pane -- a real environment reflection, Fresnel-weighted so it grazes bright. The point
// light itself is never in the scene colour (Lumos lights are not geometry), so this
// complements the specular glint from the deferred pass rather than replacing it.

uniform sampler2D SceneColor;   // unit 0: composited, lit scene colour (sRGB-encoded)
uniform sampler2D SceneDepth;   // unit 1: scene depth
uniform sampler2D GBufferId;    // unit 2: material id (R); glass panes carry id 4
uniform int HasGBuffer;         // 1 if the material G-buffer is bound

uniform mat4 InvViewProj;       // clip -> camera-relative world
uniform mat4 ViewProj;          // camera-relative world -> clip
uniform vec2 TexelSize;         // 1/width, 1/height
uniform float Strength;         // overall reflection intensity

in vec2 texCoord;
out vec4 fragColor;

const int MARCH_STEPS = 28;
const int REFINE_STEPS = 6;
const float MAX_DISTANCE = 20.0;   // blocks the ray may travel
const float THICKNESS = 0.75;      // blocks; largest depth gap still counted as a hit
const float R0 = 0.05;             // glass reflectance at normal incidence

vec3 reconstruct(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = InvViewProj * clip;
    return world.xyz / world.w;
}

float unpackDepth24(vec3 encodedDepth) {
    vec3 depthBytes = floor(encodedDepth * 255.0 + 0.5);
    return dot(depthBytes, vec3(65536.0, 256.0, 1.0)) / 16777215.0;
}

// True when the frontmost surface at uv is a glass pane (GLASS material id) that actually OWNS the
// pixel: the pane re-render is sorted only against other panes, so a pane behind an opaque wall
// stamped its id here too. The depth it wrote must still resolve to the scene surface, or it is
// occluded and must not reflect.
bool isGlassAt(vec2 uv) {
    vec4 idSample = texture(GBufferId, uv);
    float id = idSample.r * 255.0;
    if (id < 3.5 || id > 4.5) return false;
    float ownerDepth = unpackDepth24(idSample.gba);
    float sceneDepth = texture(SceneDepth, uv).r;
    vec3 ownerP = reconstruct(uv, ownerDepth);
    vec3 Ps = reconstruct(uv, sceneDepth);
    return distance(ownerP, Ps) < 0.02 + 0.01 * length(Ps);
}

// Axis-snapped pane normal from the scene depth of the neighbouring pane texels. Glass is
// overwhelmingly axis-aligned, so a near-axis derivative snaps to its exact axis (a clean, planar
// reflection); a genuinely diagonal pane keeps the derived value. Neighbours that are NOT glass
// are dropped in favour of the centre, so the estimate stays valid at pane edges.
vec3 glassNormal(vec2 uv, vec3 P) {
    vec2 rx = uv + vec2(TexelSize.x, 0.0);
    vec2 lx = uv - vec2(TexelSize.x, 0.0);
    vec2 uy = uv + vec2(0.0, TexelSize.y);
    vec2 dy = uv - vec2(0.0, TexelSize.y);
    bool rGlass = isGlassAt(rx);
    bool lGlass = isGlassAt(lx);
    bool uGlass = isGlassAt(uy);
    bool dGlass = isGlassAt(dy);

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
    // The frontmost visible surface must be a pane. The GLASS id was written depth-tested against
    // the main depth, so it is set only where a pane is the frontmost surface -- no separate
    // reconstruct-and-compare test is needed.
    if (!isGlassAt(texCoord)) discard;

    vec3 P = reconstruct(texCoord, sceneDepth);
    vec3 N = glassNormal(texCoord, P);
    if (N == vec3(0.0)) discard;
    vec3 V = normalize(P);                         // camera -> fragment (incident ray)
    if (dot(N, V) > 0.0) N = -N;                    // face the pane toward the camera
    vec3 R = reflect(V, N);

    vec3 origin = P + N * 0.03;                     // lift off the pane to avoid self-hit
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

    float fresnel = R0 + (1.0 - R0) * pow(1.0 - max(dot(N, -V), 0.0), 5.0);
    float alpha = clamp(fresnel * Strength * edge, 0.0, 1.0);
    if (alpha <= 0.0) discard;

    vec3 reflected = texture(SceneColor, hitUV).rgb;
    // A NaN/Inf sample renders as a solid black block and blends it straight onto the pane.
    // Drop the fragment rather than leak it; a missing reflection is invisible, a black hole isn't.
    if (any(isnan(reflected)) || any(isinf(reflected))) discard;
    fragColor = vec4(max(reflected, vec3(0.0)), alpha);
}
