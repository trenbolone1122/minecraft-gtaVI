#version 330
#extension GL_ARB_separate_shader_objects : require

#include <leonida:fx/grade_common.glsl>

// Leonida look, final pass. Scene: sun and fill lighting from depth normals, ambient occlusion, wet roads at night,
// water reflections, sun glitter and turquoise towards the sun, bloom, white balance, ACES tonemap, grey-blue haze.
// Sky: deeper blue zenith, grey-blue haze horizon, gold/orange glow towards the low sun, cumulus clouds.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler;
uniform sampler2D AoSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 posAt(vec2 uv) {
    return worldPos(uv, texture(DepthSampler, uv).r);
}

vec3 normalAt(vec2 uv, vec3 p) {
    vec2 px = 1.0 / InSize;
    float dist = length(p);
    vec3 l = posAt(uv - vec2(px.x, 0.0)), r = posAt(uv + vec2(px.x, 0.0));
    vec3 b = posAt(uv - vec2(0.0, px.y)), t = posAt(uv + vec2(0.0, px.y));
    vec3 dx = abs(length(r) - dist) < abs(length(l) - dist) ? r - p : p - l;
    vec3 dy = abs(length(t) - dist) < abs(length(b) - dist) ? t - p : p - b;
    vec3 n = normalize(cross(dx, dy));
    return dot(n, p) > 0.0 ? -n : n;
}

// Minecraft bakes fixed face shading into block colours: top 1.0, z faces 0.8, x faces 0.6, bottom 0.5.
float vanillaShade(vec3 n) {
    vec3 a = n * n;
    return a.y * (n.y > 0.0 ? 1.0 : 0.5) + a.z * 0.8 + a.x * 0.6;
}

float aoAt(vec2 uv) {
    vec2 px = 1.0 / InSize;
    float s = texture(AoSampler, uv).r * 0.4;
    s += texture(AoSampler, uv + vec2(px.x, px.y) * 1.5).r * 0.15;
    s += texture(AoSampler, uv + vec2(-px.x, px.y) * 1.5).r * 0.15;
    s += texture(AoSampler, uv + vec2(px.x, -px.y) * 1.5).r * 0.15;
    s += texture(AoSampler, uv + vec2(-px.x, -px.y) * 1.5).r * 0.15;
    return s;
}

void main() {
    vec3 src = texture(InSampler, texCoord).rgb;
    float strength = Params.x;
    float day = Phase.x, golden = Phase.y, night = Phase.z, rain = Phase.w;
    float overworld = Params.z;
    float underwater = Params.w;
    vec3 sun = normalize(SunDir.xyz);

    float depth = texture(DepthSampler, texCoord).r;
    bool isSky = isSkyDepth(depth);
    vec3 graded;

    if (isSky) {
        vec3 d = viewDir(texCoord);
        if (overworld > 0.5 && underwater < 0.5) {
            vec3 sky = fullSky(d);
            // keep what vanilla draws brighter than its sky: sun disc, moon and stars
            float base = luma(SkyHorizon.rgb) * 0.4 + luma(SkyZenith.rgb) * 0.6;
            float extra = max(luma(src) - max(base * SkyZenith.w + 0.08, 0.12), 0.0);
            float keep = smoothstep(0.0, 0.25, extra);
            // clouds pass in front of the stars and the moon but not the sun
            sky = mix(sky, max(sky, src), keep * mix(1.0, 0.85, night));
            graded = sky;
        } else {
            graded = src;
        }
        graded += texture(BloomSampler, texCoord).rgb * Params.y * 0.25;
    } else {
        vec3 p = worldPos(texCoord, depth);
        float dist = length(p);
        vec3 v = p / max(dist, 1.0e-4);
        vec3 n = normalAt(texCoord, p);
        vec3 c = toLinear(src);
        float near = smoothstep(180.0, 90.0, dist);

        // water: flat, facing up and blue-green. Reflect the sky, glitter in the sun, turquoise towards the sun.
        float blueish = clamp((max(src.b, src.g) - src.r - 0.08) * 5.0, 0.0, 1.0) * step(src.r, src.b + 0.02);
        float water = CloudGlow.w * blueish * smoothstep(0.93, 0.985, n.y) * (1.0 - underwater) * overworld;
        if (water > 0.0) {
            vec2 wp = CameraInfo.xz + p.xz;
            float t = CameraInfo.w;
            vec2 grad = vec2(vnoise(wp * 0.9 + vec2(t * 0.6, t * 0.2)) - vnoise(wp * 0.9 + vec2(0.37, 0.0) + vec2(t * 0.6, t * 0.2)),
                             vnoise(wp * 0.9 + vec2(t * 0.6, t * 0.2)) - vnoise(wp * 0.9 + vec2(0.0, 0.37) + vec2(t * 0.6, t * 0.2)));
            grad += vec2(vnoise(wp * 2.7 - t * 0.9) - 0.5, vnoise(wp.yx * 2.7 + t * 0.7) - 0.5) * 0.25;
            vec3 wn = normalize(vec3(grad.x * 0.35, 1.0, grad.y * 0.35));
            vec3 rd = reflect(v, wn);
            rd.y = abs(rd.y);
            vec3 refl = toLinear(fullSky(rd));
            float cosv = clamp(-dot(v, wn), 0.0, 1.0);
            float fres = 0.03 + 0.97 * pow(1.0 - cosv, 5.0);
            vec3 wc = c;
            // turquoise when looking towards the sun
            vec2 hv = normalize(v.xz + 1.0e-4), hs = normalize(sun.xz + 1.0e-4);
            float facing = max(dot(hv, hs), 0.0) * SunDir.w;
            wc = mix(wc, toLinear(vec3(0.10, 0.78, 0.74)) * max(luma(c) * 2.2, 0.08), 0.35 * facing * day);
            wc = mix(wc, refl, clamp(fres * 0.9, 0.0, 0.85));
            // sun glitter path: sharp sparkles on the wave facets around the sun reflection
            float spec = pow(max(dot(rd, sun), 0.0), mix(900.0, 300.0, golden));
            float sparkle = step(0.82, hash12(floor(wp * 6.0) + floor(t * 8.0)));
            float glint = spec * (0.4 + sparkle * 3.6) + pow(max(dot(rd, sun), 0.0), 60.0) * 0.12;
            wc += toLinear(SunColor.rgb) * glint * SunDir.w * 3.0;
            c = mix(c, wc, water * near);
        }

        // sun and fill lighting: strong sun, dim blue-grey fill, so shadow sides read dark
        float lumSrc = luma(src);
        float lit = SunDir.w * overworld * (1.0 - underwater) * near * smoothstep(0.04, 0.22, lumSrc) * (1.0 - water);
        if (lit > 0.0) {
            vec3 shadeSun = normalize(vec3(sun.x, sun.y, sun.z + 0.45 * max(sun.y, 0.0)));
            float ndl = max(dot(n, shadeSun), 0.0);
            vec3 fill = mix(vec3(0.42, 0.46, 0.56), vec3(0.36, 0.34, 0.46), golden);
            vec3 L = fill + toLinear(SunColor.rgb) * ndl * 0.85;
            vec3 f = clamp(L / max(vanillaShade(n), 0.3), vec3(0.45), vec3(1.3));
            c *= mix(vec3(1.0), f, lit);
        }

        // ambient occlusion
        float ao = DepthInfo.w > 0.5 ? aoAt(texCoord) : 1.0;
        c *= mix(1.0, ao, mix(0.85, 0.6, night));

        // wet roads at night and in rain: neon and lamps streak on surfaces facing up
        float wet = CloudShade.w * smoothstep(0.85, 0.97, n.y) * (1.0 - water) * smoothstep(120.0, 20.0, dist);
        if (wet > 0.0 && Params.y > 0.5) {
            vec3 streak = vec3(0.0);
            float wsum = 0.0;
            for (int i = 1; i <= 8; i++) {
                float fi = float(i);
                float off = fi * fi * 0.0035;
                float w = 1.0 / fi;
                streak += texture(BloomSampler, texCoord + vec2(0.0, off)).rgb * w;
                wsum += w;
            }
            streak /= wsum;
            float fres = 0.25 + 0.75 * pow(1.0 - clamp(-dot(v, n), 0.0, 1.0), 3.0);
            c = c * mix(1.0, 0.82, wet) + toLinear(streak) * wet * fres * 2.2;
        }

        // bloom: glow on lights, neon and headlights
        vec3 bloom = toLinear(texture(BloomSampler, texCoord).rgb);
        c += bloom * Params.y * mix(0.25, 1.0, clamp(night + golden * 0.5, 0.0, 1.0));

        // white balance per time of day
        vec3 wb = vec3(1.0);
        wb = mix(wb, vec3(1.04, 1.0, 0.95), day);
        wb = mix(wb, vec3(1.16, 0.95, 0.84), golden);
        wb = mix(wb, vec3(0.98, 0.99, 1.02), night);
        c *= mix(vec3(1.0), wb, overworld);

        // saturation: punchy Florida colours, neon pops at night
        float l = luma(c);
        // at night, moonlit (dim) surfaces lose the vanilla blue cast; lamp- and neon-lit pixels keep full colour
        float moonlit = night * (1.0 - smoothstep(0.06, 0.28, l)) * (1.0 - water);
        c = mix(vec3(l), c, 1.14 + golden * 0.1 + night * 0.04 - rain * 0.2 - moonlit * 0.75);
        c *= 1.0 - moonlit * 0.18;

        // ACES-style filmic tonemap
        float exposure = 1.08 + night * 0.3 + golden * 0.08 + underwater * 0.2;
        graded = toGamma(aces(c * exposure)) * 1.04;

        // no extra haze layer: distance fog is vanilla's (pushed to the render distance edge by LeonidaLook)
        // vanilla's render distance edge fade blends terrain into its flat fog colour, which then went through the
        // grade above and no longer matches our sky: re-blend edge pixels into the exact sky we draw behind them
        if (overworld > 0.5 && underwater < 0.5 && Haze.y > Haze.x) {
            float cyl = max(length(p.xz), abs(p.y));
            float edge = clamp((cyl - Haze.x) / (Haze.y - Haze.x), 0.0, 1.0);
            graded = mix(graded, fullSky(v), smoothstep(0.0, 0.85, edge));
        }
    }

    // vignette
    vec2 vg = texCoord - 0.5;
    graded *= 1.0 - dot(vg, vg) * mix(0.30, 0.5, night);

    fragColor = vec4(mix(src, graded, strength), 1.0);
}
