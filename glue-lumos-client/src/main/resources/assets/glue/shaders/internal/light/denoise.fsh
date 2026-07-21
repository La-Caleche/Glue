#version 150

// Edge-avoiding (a-trous style) denoise of the accumulated HDR light buffer, run once per
// axis as a separable approximation. Each tap is weighted by how far its depth deviates from
// the LOCAL DEPTH PLANE -- the linear extrapolation of the surface through the centre along
// the blur axis. A tap that lands on the same surface matches the prediction and smooths
// freely at ANY viewing angle, including a grazing face where a plain world-distance weight
// collapses (neighbouring pixels span too much world space) and leaves the grain untouched.
// A depth discontinuity (a silhouette edge) or a change of slope (a block corner) breaks the
// prediction, so those edges are preserved.

uniform sampler2D LightTex;    // unit 0: HDR light to blur (RGBA16F)
uniform sampler2D GuideDepth;  // unit 1: scene depth (nonlinear main depth)
uniform vec2 Direction;        // one texel step in uv along the blur axis

const int RADIUS = 4;
// How many local per-texel slopes of depth deviation still count as the same surface.
const float SLOPE_K = 3.0;
// Tolerance on a flat surface (slope ~ 0): a few depth quanta, so quantisation still smooths
// but a real edge (which jumps far more at the near/mid ranges lights reach) is preserved.
const float DEPTH_FLOOR = 1e-5;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float centerDepth = texture(GuideDepth, texCoord).r;
    vec4 center = texture(LightTex, texCoord);

    // Sky / no geometry: nothing to smooth over.
    if (centerDepth >= 1.0) {
        fragColor = center;
        return;
    }

    // Local per-texel depth slope along the blur axis. Skip it when a neighbour fell off
    // geometry, so a silhouette does not seed a garbage plane.
    float dPlus = texture(GuideDepth, texCoord + Direction).r;
    float dMinus = texture(GuideDepth, texCoord - Direction).r;
    float slope = (dPlus < 1.0 && dMinus < 1.0) ? 0.5 * (dPlus - dMinus) : 0.0;
    float tolerance = SLOPE_K * abs(slope) + DEPTH_FLOOR;
    float invToleranceSq = 1.0 / (tolerance * tolerance);

    vec4 sum = center;
    float weightSum = 1.0;
    for (int i = 1; i <= RADIUS; i++) {
        float spatial = exp(-float(i * i) / (2.0 * float(RADIUS) * float(RADIUS)) * 9.0);
        float step = float(i);

        vec2 uvP = texCoord + Direction * step;
        float aP = texture(GuideDepth, uvP).r;
        if (aP < 1.0) {
            float dev = aP - (centerDepth + slope * step);
            float w = spatial * exp(-dev * dev * invToleranceSq);
            sum += texture(LightTex, uvP) * w;
            weightSum += w;
        }

        vec2 uvN = texCoord - Direction * step;
        float aN = texture(GuideDepth, uvN).r;
        if (aN < 1.0) {
            float dev = aN - (centerDepth - slope * step);
            float w = spatial * exp(-dev * dev * invToleranceSq);
            sum += texture(LightTex, uvN) * w;
            weightSum += w;
        }
    }

    // Blur all four channels identically: alpha carries glass-transmission metadata the
    // composite reads alongside rgb, so it must stay consistent with the colour.
    fragColor = sum / weightSum;
}
