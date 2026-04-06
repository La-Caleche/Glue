#version 150

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SunConfig {
    float Progress; // 0.0 to 1.0
};

in vec2 texCoord;
out vec4 fragColor;

// --- MATH UTILS ---
float hash(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.31);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
    mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = rot * p * 2.1;
        a *= 0.5;
    }
    return v;
}

vec3 getPlasmaColor(float t) {
    vec3 c1 = vec3(0.5, 0.0, 0.0);   // Deep Blood Red
    vec3 c2 = vec3(1.0, 0.3, 0.0);   // Hot Orange
    vec3 c3 = vec3(1.0, 0.8, 0.2);   // Solar Yellow
    vec3 c4 = vec3(1.0, 1.0, 1.0);   // White Core

    float step1 = smoothstep(0.0, 0.4, t);
    float step2 = smoothstep(0.4, 0.7, t);
    float step3 = smoothstep(0.7, 1.0, t);

    return mix(mix(mix(c1, c2, step1), c3, step2), c4, step3);
}

void main() {
    vec2 uv = texCoord;

    // --- 1. SLOWER TIMING & SMOOTHER START ---
    // Reduced from 10.0 to 4.0 to make the noise flow "longer" and slower
    float time = Progress * 4.0;

    // Using pow(Progress, 2.5) removes the initial flash by making the start very gradual
    float eased = pow(Progress, 2.5);

    // --- 2. SUBTLE HEAT DISTORTION ---
    float aberration = eased * 0.012;
    float r = texture(InSampler, uv + vec2(aberration, 0.0)).r;
    float g = texture(InSampler, uv).g;
    float b = texture(InSampler, uv - vec2(aberration, 0.0)).b;
    vec4 scene = vec4(r, g, b, 1.0);

    // --- 3. DOMAIN WARPED PLASMA ---
    // Reduced warp intensity for a more "majestic" look
    vec2 warp = vec2(fbm(uv * 2.0 + time * 0.3), fbm(uv * 2.0 - time * 0.2));
    float plasma = fbm(uv * 3.5 + warp * 1.5 + time * 0.15);
    vec3 sunColor = getPlasmaColor(plasma);

    // --- 4. RADIAL GLOW ---
    vec2 centered = (uv - 0.5) * vec2(InSize.x / InSize.y, 1.0);
    float dist = length(centered);

    // Core glow that only appears as Progress advances
    float glow = exp(-dist * 4.0) * eased;
    sunColor += glow * 0.5;

    // --- 5. FINAL COMPOSITION ---
    // The mix is now driven by the slower 'eased' value
    vec3 finalColor = mix(scene.rgb, sunColor, eased * 0.9);

    // Central flare (Sun core)
    float flare = smoothstep(0.4, 0.0, dist) * eased;
    finalColor += vec3(1.0, 0.9, 0.7) * flare * 0.5;

    // Soft Heat Vignette
    float vignette = smoothstep(0.2, 1.0, dist);
    finalColor = mix(finalColor, vec3(0.15, 0.0, 0.0), vignette * eased * 0.8);

    // White flash only at the VERY end (Progress > 0.9)
//    float endFlash = smoothstep(0.9, 1.0, Progress);
//    finalColor = mix(finalColor, vec3(1.0), endFlash);

    fragColor = vec4(finalColor, 1.0);
}