#version 330
#extension GL_ARB_separate_shader_objects : require

#include <leonida:fx/grade_common.glsl>

// Screen space ambient occlusion: darker street canyons, wall feet and corners (the baked AO of the 3D map).

uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 posAt(vec2 uv) {
    return worldPos(uv, texture(DepthSampler, uv).r);
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (isSkyDepth(depth) || DepthInfo.w < 0.5) {
        fragColor = vec4(1.0);
        return;
    }
    vec2 px = 1.0 / InSize;
    vec3 p = worldPos(texCoord, depth);
    float dist = length(p);
    if (dist > 96.0) {
        fragColor = vec4(1.0);
        return;
    }
    // normal from the closer neighbour on each axis (avoids smearing across edges)
    vec3 l = posAt(texCoord - vec2(px.x, 0.0)), r = posAt(texCoord + vec2(px.x, 0.0));
    vec3 b = posAt(texCoord - vec2(0.0, px.y)), t = posAt(texCoord + vec2(0.0, px.y));
    vec3 dx = abs(length(r) - dist) < abs(length(l) - dist) ? r - p : p - l;
    vec3 dy = abs(length(t) - dist) < abs(length(b) - dist) ? t - p : p - b;
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, p) > 0.0) n = -n;

    // tangent frame with a per pixel rotation (4x4 interleaved)
    float ang = hash12(floor(gl_FragCoord.xy)) * 6.2831853;
    vec3 tg = normalize(abs(n.y) < 0.9 ? cross(n, vec3(0, 1, 0)) : cross(n, vec3(1, 0, 0)));
    vec3 bt = cross(n, tg);
    float ca = cos(ang), sa = sin(ang);
    vec3 t1 = tg * ca + bt * sa, t2 = -tg * sa + bt * ca;

    float radius = mix(1.1, 2.2, clamp(dist / 64.0, 0.0, 1.0));
    float occ = 0.0;
    const int N = 10;
    for (int i = 0; i < N; i++) {
        float fi = (float(i) + 0.5) / float(N);
        float a = fi * 6.2831853 * 2.618;
        float rr = radius * mix(0.25, 1.0, fi * fi);
        float elev = mix(0.15, 0.85, fract(fi * 7.31));
        vec3 dir = normalize(t1 * cos(a) + t2 * sin(a)) * sqrt(1.0 - elev * elev) + n * elev;
        vec3 s = p + n * 0.04 + dir * rr;
        vec4 clip = ViewProj * vec4(s, 1.0);
        if (clip.w <= 0.01) continue;
        vec2 suv = clip.xy / clip.w * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0) continue;
        float sd = texture(DepthSampler, suv).r;
        if (isSkyDepth(sd)) continue;
        vec3 sp = worldPos(suv, sd);
        float sceneDist = length(sp);
        float sampleDist = length(s);
        float diff = sampleDist - sceneDist;
        if (diff > 0.03) {
            occ += smoothstep(0.0, 1.0, radius * 1.6 / max(diff, 0.001) - 0.5);
        }
    }
    float ao = 1.0 - occ / float(N);
    ao = mix(1.0, ao, smoothstep(96.0, 48.0, dist));
    fragColor = vec4(vec3(clamp(ao, 0.0, 1.0)), 1.0);
}
