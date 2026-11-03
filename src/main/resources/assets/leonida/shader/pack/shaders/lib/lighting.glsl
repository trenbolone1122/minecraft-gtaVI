// Leonida Shaders: surface shading shared by the deferred pass and the forward (translucent) passes.
// Needs common, atmosphere, sky and shadow (with SHADOW_SAMPLING). Original code, GPL-3.0-or-later.

// warm but not gold: the old (1.0, 0.60, 0.30) painted lit facades orange at night
const vec3 BLOCK_LIGHT = vec3(1.0, 0.88, 0.75);
const float EMISSIVE_I = 2.6;
// set by the caller from the sun height: emissives keep about the same on-screen brightness while the
// exposure opens up at night, so neon stays coloured instead of clipping to white
float g_emitScale = 1.0;
// 0 at night, 1 in full daylight: sunlight outshines lamps, interior lights and lit panes
float g_dayF = 0.0;

float ggxD(float NdotH, float a) {
	float a2 = a * a;
	float d = NdotH * NdotH * (a2 - 1.0) + 1.0;
	return a2 / (PI * d * d);
}
vec3 fresnelSchlick(float c, vec3 f0) { return f0 + (1.0 - f0) * pow5(1.0 - c); }

struct Surface {
	vec3 albedo;    // linear
	vec3 N;         // world space shading normal
	vec2 lm;        // block, sky light 0..1
	float smoothness;
	float f0;
	float emissive;
	int mat;
};

vec3 sunSpecular(Surface s, vec3 V, vec3 L, vec3 lightCol, vec3 shadow) {
	if (s.smoothness < 0.05) return vec3(0.0);
	vec3 H = normalize(V + L);
	float NdotL = saturate(dot(s.N, L));
	float NdotV = saturate(dot(s.N, V)) + 1e-3;
	float rough = pow2(1.0 - s.smoothness);
	float a = max(rough, 0.002);
	float D = ggxD(saturate(dot(s.N, H)), a);
	float k = a * 0.5;
	float vis = 1.0 / ((NdotL * (1.0 - k) + k) * (NdotV * (1.0 - k) + k) * 4.0);
	bool metal = s.f0 > 0.9;
	vec3 f0 = metal ? s.albedo : vec3(s.f0);
	vec3 F = fresnelSchlick(saturate(dot(H, V)), f0);
	return min(lightCol * shadow * NdotL * D * vis * F, vec3(40.0));
}

// playerPos and directions in world axes; V points from the surface to the camera
vec3 shadeSurface(Surface s, vec3 playerPos, vec3 V, vec3 L, vec3 lightCol, vec3 ambient, vec2 fragCoord, out vec3 shadowOut) {
	float NdotL = dot(s.N, L);
	bool foliage = s.mat == MAT_FOLIAGE;
	vec3 shadow = vec3(0.0);
	float skyAccess = smoothstep(0.0, 0.2, s.lm.y);
	if ((NdotL > 0.0 || foliage) && skyAccess > 0.0) {
		shadow = sampleShadow(playerPos, foliage ? L : s.N, foliage ? 1.0 : NdotL, fragCoord) * skyAccess;
	}
	shadowOut = shadow;
	float diff = foliage ? (saturate(NdotL) * 0.6 + 0.4) : saturate(NdotL);
	vec3 direct = lightCol * shadow * diff;
	// sky fill, stronger on surfaces that face up; vanilla sky light shapes it indoors
	float skyL = s.lm.y * s.lm.y * s.lm.y;
	vec3 amb = ambient * skyL * (0.65 + 0.35 * s.N.y);
	// warm bounce from sunlit streets
	amb += lightCol * 0.035 * skyL * (0.6 - 0.4 * s.N.y);
	amb += vec3(0.006, 0.007, 0.009); // minimum so caves are not pure black
	// level 15 block light on a surface that is not itself a light is a fullbright quad (glow overlays, emissive
	// model layers) on a wall: treat it as level 14 so solid walls never light up as glowing panels
	bool emitter = s.mat == MAT_NEON || s.mat == MAT_LAMP || s.mat == MAT_WINDOW || s.mat == MAT_TOWER_GLASS || s.mat == MAT_GLASS;
	float lx = (!emitter && s.lm.x > 0.97) ? 0.9 : s.lm.x;
	vec3 blockL = BLOCK_LIGHT * (pow(lx, 4.0) * 0.9 + lx * lx * 0.08);
	// outdoor facades (open sky, vertical): street lamps and signs only graze them, keep the spill low
	if (abs(s.N.y) < 0.5) blockL *= mix(1.0, 0.6, smoothstep(0.7, 1.0, s.lm.y));
	// lit-window overlays are drawn fullbright: keep them a soft cool glow behind glass, not bright panels
	if (s.mat == MAT_WINDOW || s.mat == MAT_TOWER_GLASS || s.mat == MAT_GLASS) blockL *= 0.15;
	blockL *= 1.0 - 0.65 * g_dayF;
	bool metal = s.f0 > 0.9;
	vec3 diffuse = s.albedo * (direct + amb + blockL) * (metal ? 0.25 : 1.0);
	vec3 spec = sunSpecular(s, V, L, lightCol, shadow);
	// neon and lamps: push the hue (glass tubes are deeply coloured, pale textures read as white otherwise)
	float el = luma(s.albedo);
	bool pane = s.mat == MAT_WINDOW || s.mat == MAT_TOWER_GLASS || s.mat == MAT_GLASS;
	vec3 ec = max(mix(vec3(el), s.albedo, pane ? 1.0 : 1.9), vec3(0.0));
	// panes glow at under half the neon strength: neon must stay the brightest light at night
	vec3 emit = ec * s.emissive * EMISSIVE_I * g_emitScale * (pane ? 0.4 : 1.0);
	// by day only neon keeps its glow; window panes and lamps go dark
	emit *= s.mat == MAT_NEON ? 1.0 : (pane ? 1.0 - g_dayF : 1.0 - 0.9 * g_dayF);
	return diffuse + spec + emit;
}
