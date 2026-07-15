#version 150

// Edge-aware (cross-bilateral) denoise of the accumulated HDR light buffer, run once
// per axis as a separable approximation. The shadow and glass-tint sampling is
// stochastic (rotated Vogel disks, no temporal filter) and normals for non-terrain
// surfaces are reconstructed from depth, so the light buffer carries high-frequency
// grain. A plain Gaussian would smear light across geometry edges; instead each tap is
// weighted by how far its reconstructed WORLD POSITION sits from the centre's. A depth
// discontinuity is a large position jump, so its weight collapses and the edge is
// preserved, while samples within one surface -- where positions stay close -- smooth
// freely.

uniform sampler2D LightTex;    // unit 0: HDR light to blur (RGBA16F)
uniform sampler2D GuideDepth;  // unit 1: scene depth (nonlinear main depth)
uniform mat4 InvViewProj;      // clip -> camera-relative world, for edge weighting
uniform vec2 Direction;        // one texel step in uv along the blur axis
uniform float PositionSigma;   // world-space falloff in blocks (edge preservation)

const int RADIUS = 3;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstruct(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 worldH = InvViewProj * clip;
    return worldH.xyz / worldH.w;
}

void main() {
    float centerDepth = texture(GuideDepth, texCoord).r;
    vec4 center = texture(LightTex, texCoord);

    // Sky / no geometry: no surface to smooth over, and reconstruct() would divide by a
    // degenerate w. Pass the centre sample through untouched.
    if (centerDepth >= 1.0) {
        fragColor = center;
        return;
    }

    vec3 P0 = reconstruct(texCoord, centerDepth);
    float invSigmaSq = 1.0 / (PositionSigma * PositionSigma);

    vec4 sum = center;
    float weightSum = 1.0;
    for (int i = 1; i <= RADIUS; i++) {
        float spatial = exp(-float(i * i) / (2.0 * float(RADIUS) * float(RADIUS)) * 9.0);
        vec2 offset = Direction * float(i);

        vec2 uvP = texCoord + offset;
        float dP = texture(GuideDepth, uvP).r;
        if (dP < 1.0) {
            vec3 P = reconstruct(uvP, dP);
            vec3 delta = P - P0;
            float w = spatial * exp(-dot(delta, delta) * invSigmaSq);
            sum += texture(LightTex, uvP) * w;
            weightSum += w;
        }

        vec2 uvN = texCoord - offset;
        float dN = texture(GuideDepth, uvN).r;
        if (dN < 1.0) {
            vec3 P = reconstruct(uvN, dN);
            vec3 delta = P - P0;
            float w = spatial * exp(-dot(delta, delta) * invSigmaSq);
            sum += texture(LightTex, uvN) * w;
            weightSum += w;
        }
    }

    // Blur all four channels identically: alpha carries glass-transmission metadata the
    // composite reads alongside rgb, so it must stay consistent with the colour.
    fragColor = sum / weightSum;
}
