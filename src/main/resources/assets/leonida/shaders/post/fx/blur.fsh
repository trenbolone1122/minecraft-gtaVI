#version 330
#extension GL_ARB_separate_shader_objects : require

// Leonida bloom, step 2: 9 tap gaussian along one axis, spread by Step texels.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LeonidaBlur {
    vec2 Dir;
    float Step;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = Dir * Step / InSize;
    vec3 s = texture(InSampler, texCoord).rgb * 0.2270270270;
    s += (texture(InSampler, texCoord + o * 1.3846153846).rgb + texture(InSampler, texCoord - o * 1.3846153846).rgb) * 0.3162162162;
    s += (texture(InSampler, texCoord + o * 3.2307692308).rgb + texture(InSampler, texCoord - o * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(s, 1.0);
}
