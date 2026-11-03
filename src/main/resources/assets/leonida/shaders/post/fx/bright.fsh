#version 330
#extension GL_ARB_separate_shader_objects : require

#include <leonida:fx/grade_common.glsl>

// Leonida bloom, step 1: keep only bright and neon pixels (lamps, neon tubes, headlights, the sun).

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float peak = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    float sat = peak > 0.001 ? (peak - lo) / peak : 0.0;
    // lower threshold at night so lamps and neon glow; saturated colours count extra (neon signs)
    float nightish = clamp(Phase.z + Phase.y * 0.4, 0.0, 1.0);
    float threshold = mix(0.90, 0.55, nightish) - sat * mix(0.10, 0.18, nightish);
    float w = smoothstep(threshold, threshold + 0.16, peak);
    fragColor = vec4(c * w * (1.0 + sat * mix(0.5, 1.1, nightish)), 1.0);
}
