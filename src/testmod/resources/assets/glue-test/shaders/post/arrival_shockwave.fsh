#version 150

// -----------------------------------------------------------------------------
// Example: Arrival Shockwave (Post Shader)
// Description: Renders a screen-space expanding radial ripple with glowing
// concentric rings and a blinding center flash.
// -----------------------------------------------------------------------------

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ShockwaveConfig {
    float Progress; // 1.0 (start) -> 0.0 (end)
};

in vec2 texCoord;
out vec4 fragColor;

// Smooth cubic easing
float smootherStep(float t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

void main() {
    vec2 uv = texCoord;
    vec2 centered = uv - 0.5;

    // Aspect-ratio corrected distance
    float aspect = InSize.x / InSize.y;
    vec2 aspectCoord = vec2(centered.x * aspect, centered.y);
    float dist = length(aspectCoord);

    float decay = 1.0 - Progress;
    float eased = smootherStep(decay);

    // Primary wave distortion
    float waveRadius = eased * 0.85;
    float waveDist = abs(dist - waveRadius);
    float waveShape = smoothstep(0.18, 0.0, waveDist);

    vec2 radial = dist > 0.0001 ? normalize(centered) : vec2(0.0);
    float rippleStr = waveShape * (1.0 - eased) * 0.06;
    vec2 warpedUV = uv + radial * rippleStr;

    vec4 col = texture(InSampler, clamp(warpedUV, 0.0, 1.0));

    // Glowing rings
    float ring1Glow = waveShape * max(0.0, 1.0 - eased * 1.2);
    
    float ring2R = eased * 0.50;
    float ring2Glow = smoothstep(0.10, 0.0, abs(dist - ring2R)) * max(0.0, 1.0 - eased * 1.6) * 0.6;

    float ring3R = eased * 1.1;
    float ring3Glow = smoothstep(0.05, 0.0, abs(dist - ring3R)) * max(0.0, 1.0 - eased * 1.8) * 0.4;

    col.rgb += vec3(1.0, 0.85, 0.3) * ring1Glow;
    col.rgb += vec3(0.6, 0.2, 1.0) * ring2Glow;
    col.rgb += vec3(1.0) * ring3Glow;

    // Initial blinding flash
    float flashDecay = max(0.0, Progress - 0.8) / 0.2;
    float flashIntensity = smootherStep(flashDecay);
    vec3 flashColor = mix(vec3(1.0), vec3(1.0, 0.95, 0.7), 0.4);
    col.rgb = mix(col.rgb, flashColor, flashIntensity * 0.95);

    fragColor = col;
}
