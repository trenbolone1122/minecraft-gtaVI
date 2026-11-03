// Leonida Shaders: shared helpers. Original code, GPL-3.0-or-later.
#include "/lib/settings.glsl"

#define PI 3.14159265359
#define TAU 6.28318530718

// material ids stored in colortex2.r * 255
#define MAT_DEFAULT 0
#define MAT_WATER 1
#define MAT_GLASS 2
#define MAT_TOWER_GLASS 3
#define MAT_WINDOW 4
#define MAT_NEON 5
#define MAT_ASPHALT 6
#define MAT_METAL 7
#define MAT_FOLIAGE 8
#define MAT_LAMP 9
#define MAT_POLISHED 10
#define MAT_SAND 11
#define MAT_ENTITY 20
#define MAT_HAND 21

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }
float pow2(float x) { return x * x; }
float pow5(float x) { float x2 = x * x; return x2 * x2 * x; }

// octahedral normal packing into two [0,1] channels
vec2 octWrap(vec2 v) { return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0); }
vec2 encodeNormal(vec3 n) {
	n /= abs(n.x) + abs(n.y) + abs(n.z);
	n.xy = n.z >= 0.0 ? n.xy : octWrap(n.xy);
	return n.xy * 0.5 + 0.5;
}
vec3 decodeNormal(vec2 e) {
	e = e * 2.0 - 1.0;
	vec3 n = vec3(e, 1.0 - abs(e.x) - abs(e.y));
	float t = saturate(-n.z);
	n.xy += vec2(n.x >= 0.0 ? -t : t, n.y >= 0.0 ? -t : t);
	return normalize(n);
}

// interleaved gradient noise (Jimenez 2014)
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hash13(vec3 p3) { p3 = fract(p3 * 0.1031); p3 += dot(p3, p3.zyx + 31.32); return fract((p3.x + p3.y) * p3.z); }
// lit office and condo windows at night: per-window brightness, warm/neutral/cool hue and some lowered blinds,
// so a tower reads as many separate rooms instead of one white grid. One window = one block cell.
vec3 litWindowTint(vec3 worldPos, vec3 N) {
	vec3 cell = floor(worldPos - N * 0.05);
	float h1 = hash13(cell + 0.37);
	float h2 = hash13(cell * 1.71 + 17.3);
	float h3 = hash13(cell * 0.93 + 41.7);
	// only a minority of cells are lit; the rest stay a dim pane so neon is the brightest thing at night
	if (h1 > 0.30) return vec3(0.10, 0.11, 0.13);
	float bright = mix(0.30, 0.60, fract(h1 * 13.7));
	vec3 tint = h2 < 0.55 ? vec3(0.90, 0.96, 1.05)          // cool white office
		: (h2 < 0.90 ? vec3(0.80, 0.90, 1.05)               // pale blue screen light
		: vec3(0.96, 0.95, 0.94));                          // neutral
	if (h3 < 0.28) {
		// blinds lowered to a random height, with slats
		float lowered = 0.30 + 0.55 * fract(h3 * 7.13);
		float y = fract(worldPos.y + 0.001);
		if (y > 1.0 - lowered) bright *= 0.25 + 0.20 * step(0.5, fract(y * 9.0));
	}
	return tint * bright;
}
float vnoise(vec2 p) {
	vec2 i = floor(p), f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

vec3 projectAndDivide(mat4 m, vec3 p) { vec4 h = m * vec4(p, 1.0); return h.xyz / h.w; }
vec3 screenToView(vec3 screen, mat4 projInv) { return projectAndDivide(projInv, screen * 2.0 - 1.0); }
vec3 viewToScreen(vec3 view, mat4 proj) { return projectAndDivide(proj, view) * 0.5 + 0.5; }
float linearDepth(float d, float n, float f) { return (2.0 * n * f) / (f + n - (d * 2.0 - 1.0) * (f - n)); }

// vanilla lightmap coords to [0,1] block and sky light
vec2 lightmapLevels(vec2 lmcoord) { return clamp((lmcoord - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0); }
