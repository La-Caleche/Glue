#version 150

// -----------------------------------------------------------------------------
// Example: Shattered Screen (Post Shader)
// Description: Fractures the screen using a Voronoi data texture, pushing
// shards outward radially with chromatic aberration.
// -----------------------------------------------------------------------------

// Scene framebuffer
uniform sampler2D InSampler;
// Voronoi crack data texture
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Custom uniform block for effect parameters
layout(std140) uniform ShatteredConfig {
    float Intensity;         // 0.0 = no effect, 1.0 = full effect
    float MaxOffset;         // Shard displacement magnitude
    float ChromaticStrength; // Chromatic aberration strength
    float FlashIntensity;    // 0.0 = no flash, 1.0 = full white
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

    // Early out if effect is invisible
    if (Intensity < 0.001 && FlashIntensity < 0.001) {
        fragColor = sceneColor;
        return;
    }

    // The data texture encodes:
    // R: Rotation angle
    // G: Displacement magnitude
    // B: Secondary rotation
    vec4 data = texture(DataSampler, texCoord);

    // White/near-white indicates the unaffected center area.
    bool isShattered = (data.r < 0.99 || data.g < 0.99 || data.b < 0.99) && data.a > 0.01;
    vec4 result;

    if (isShattered && Intensity > 0.001) {
        float primaryAngle = data.r * 6.2831853;
        float displacement = data.g * MaxOffset * Intensity;
        float secondaryAngle = (data.b - 0.5) * -6.2831853;

        mat2 primaryRot = rotate2D(primaryAngle);
        mat2 secondaryRot = rotate2D(secondaryAngle * MaxOffset * Intensity);

        vec2 offset = primaryRot * vec2(0.0, displacement);

        vec2 centeredUV = texCoord - 0.5;
        vec2 rotatedUV = secondaryRot * centeredUV + 0.5;

        // Chromatic aberration
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

    // Flash effect
    if (FlashIntensity > 0.001) {
        result = mix(result, vec4(1.0), FlashIntensity);
    }

    fragColor = result;
}