#version 150

// Scene framebuffer (sampler_name "In" + "Sampler" suffix)
uniform sampler2D InSampler;

// Voronoi crack data texture (sampler_name "Data" + "Sampler" suffix)
uniform sampler2D DataSampler;

// Auto-provided by PostPass: OutSize, then per-input sizes
layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Custom uniform block for effect parameters — updated at runtime
layout(std140) uniform ShatteredConfig {
    float Intensity;        // 0.0 = no effect, 1.0 = full effect
    float MaxOffset;        // Shard displacement magnitude
    float ChromaticStrength; // Chromatic aberration strength
    float FlashIntensity;   // 0.0 = no flash, 1.0 = full white
};

in vec2 texCoord;

out vec4 fragColor;

mat2 rotate2D(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c);
}

void main() {
    vec4 sceneColor = texture(InSampler, texCoord);

    // Early out if effect is basically invisible
    if (Intensity < 0.001 && FlashIntensity < 0.001) {
        fragColor = sceneColor;
        return;
    }

    // Sample the data texture — R encodes rotation angle,
    // G encodes displacement magnitude, B encodes a secondary rotation.
    // White (1,1,1) regions = center/no effect.
    vec4 data = texture(DataSampler, texCoord);

    // The data texture uses white/near-white for the unaffected center area.
    bool isShattered = (data.r < 0.99 || data.g < 0.99 || data.b < 0.99)
                    && data.a > 0.01;

    vec4 result;

    if (isShattered && Intensity > 0.001) {
        // Decode angles and displacement from the data channels
        float primaryAngle = data.r * 6.2831853;           // R → [0, 2π]
        float displacement = data.g * MaxOffset * Intensity; // G → scaled offset
        float secondaryAngle = (data.b - 0.5) * -6.2831853; // B → signed rotation

        // Build rotation/distortion — scale by intensity for smooth fade
        mat2 primaryRot = rotate2D(primaryAngle);
        mat2 secondaryRot = rotate2D(secondaryAngle * MaxOffset * Intensity);

        // Compute UV offset for the shard
        vec2 offset = primaryRot * vec2(0.0, displacement);

        // Apply secondary rotation around screen center
        vec2 centeredUV = texCoord - 0.5;
        vec2 rotatedUV = secondaryRot * centeredUV + 0.5;

        // Chromatic aberration: split R/G/B channels with slightly different offsets
        float chromaScale = ChromaticStrength * Intensity;
        vec2 redOffset   = offset * (1.0 + chromaScale);
        vec2 greenOffset = offset;
        vec2 blueOffset  = offset + rotate2D(1.5707963) * offset * chromaScale;

        float r = texture(InSampler, rotatedUV - redOffset).r;
        float g = texture(InSampler, rotatedUV - greenOffset).g;
        float b = texture(InSampler, rotatedUV - blueOffset).b;

        result = vec4(r, g, b, 1.0);
    } else {
        result = sceneColor;
    }

    // --- Flash effect: blend toward white ---
    // FlashIntensity > 0 overlays a bright white flash on the scene,
    // simulating the impact moment.
    if (FlashIntensity > 0.001) {
        result = mix(result, vec4(1.0), FlashIntensity);
    }

    fragColor = result;
}