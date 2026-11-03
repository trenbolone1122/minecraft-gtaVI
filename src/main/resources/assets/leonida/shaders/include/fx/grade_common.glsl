// Shared uniforms and helpers of the Leonida look post passes.
// Lighting recipe after the leonidastate3d.com renderer: strong sun with a dim fill, grey-blue haze, deeper blue
// zenith, fair-weather cumulus, gold to deep orange horizon at golden hour, ACES-style filmic tonemap.

layout(std140) uniform LeonidaGrade {
    vec4 Phase;       // day, golden, night, rain
    vec4 Params;      // strength, bloom on, sky dimension (1 overworld), underwater
    vec4 DepthInfo;   // near, far, zZeroToOne, ao on
    vec4 Haze;        // x: render distance fog start, y: end (blocks, as vanilla), z unused, w: haze amount
    mat4 InvViewProj; // clip -> camera relative world position
    mat4 ViewProj;    // camera relative world position -> clip
    vec4 SunDir;      // world direction to the sun (moon at night), w: sun light strength 0..1
    vec4 SunColor;    // rgb sun colour (white, gold, deep orange), w: sunset glow 0..1
    vec4 SkyZenith;   // display rgb, w: sky gain
    vec4 SkyHorizon;  // display rgb (also the haze colour), w: golden warmth of the low sky
    vec4 CloudLit;    // linear-ish display rgb of sunlit cloud tops, w: cloud cover 0..1 (0: no clouds)
    vec4 CloudShade;  // rgb of cloud cores and undersides, w: wet road amount
    vec4 CloudGlow;   // rgb glow of clouds towards the sun, w: water effects on
    vec4 CameraInfo;  // camera world position modulo 4096, w: time in seconds
};

vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toGamma(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

bool isSkyDepth(float d) {
    return d <= 0.0000001; // reversed z: the cleared far plane is 0
}

// camera relative world position of a screen point
vec3 worldPos(vec2 uv, float depth) {
    float z = DepthInfo.z > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, z, 1.0);
    return p.xyz / p.w;
}

// world view direction through a screen point
vec3 viewDir(vec2 uv) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, DepthInfo.z > 0.5 ? 0.5 : 0.0, 1.0);
    return normalize(p.xyz / p.w);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int k = 0; k < 5; k++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return s;
}

// Sky gradient with a gold/orange glow towards the low sun, in display space.
vec3 skyGradient(vec3 d) {
    vec3 sd = normalize(SunDir.xyz);
    float up = clamp(d.y, -0.2, 1.0);
    float h = pow(1.0 - clamp(up, 0.0, 1.0), 4.0);
    vec3 c = mix(SkyZenith.rgb, SkyHorizon.rgb, h);
    // below the horizon (seen from the air past the render distance): read as distant sea under light haze,
    // not as a grey fog wall
    vec3 sea = mix(SkyHorizon.rgb, SkyHorizon.rgb * vec3(0.55, 0.86, 0.95), 0.75);
    c = up < 0.0 ? mix(SkyHorizon.rgb, sea, smoothstep(0.0, -0.12, up)) : c;
    float toward = pow(max(dot(normalize(vec3(d.x, max(d.y, 0.0), d.z)), sd), 0.0), 3.0);
    // golden hour: the low sky goes gold, most of all towards the sun, deep orange right at the horizon
    float warm = SkyHorizon.w * pow(1.0 - clamp(up, 0.0, 1.0), 3.0) * (0.45 + 0.55 * toward);
    c *= mix(vec3(1.0), vec3(1.3, 0.86, 0.58), warm);
    // wide haze glow around the sun by day
    float mie = pow(max(dot(d, sd), 0.0), 8.0) * SunDir.w;
    c += SunColor.rgb * mie * 0.18;
    return c * SkyZenith.w;
}

// Fair-weather cumulus on a flat layer, lit from the sun.
vec3 cloudLayer(vec3 d, vec3 sky) {
    float cover = CloudLit.w;
    if (d.y <= 0.0 || cover <= 0.0) return sky;
    vec3 sd = normalize(SunDir.xyz);
    float toward = pow(max(dot(d, sd), 0.0), 3.0);
    float t = CameraInfo.w;
    vec2 p = d.xz / (d.y + 0.08) * 1.4 + vec2(t * 0.004, t * 0.0015) + CameraInfo.xz * 0.0004;
    float n = fbm(p);
    float lo = mix(0.70, 0.40, cover);
    float cov = smoothstep(lo, lo + 0.24, n) * smoothstep(0.01, 0.12, d.y);
    float core = smoothstep(lo + 0.18, lo + 0.5, n);
    vec3 c = mix(CloudLit.rgb, CloudShade.rgb, core * 0.9) + CloudGlow.rgb * toward * (1.0 - 0.6 * core);
    return mix(sky, c, cov * 0.92);
}

vec3 fullSky(vec3 d) {
    return cloudLayer(d, skyGradient(d));
}

// Narkowicz ACES fit
vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}
