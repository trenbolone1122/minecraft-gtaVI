// Leonida Shaders: forward lighting for translucents. Water writes its surface data for composite (refraction,
// depth colour, reflections); glass and particles blend straight into the lit scene. GPL-3.0-or-later.
#define SHADOW_SAMPLING
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/lib/shadow.glsl"
#include "/lib/lighting.glsl"
#include "/lib/water.glsl"
#include "/program/frame.glsl"

uniform sampler2D gtexture;
uniform sampler2D depthtex1;
uniform vec4 entityColor;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float near, far;
uniform float viewWidth, viewHeight;
uniform mat4 gbufferProjectionInverse;
uniform float rainStrength;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalW;
in vec3 playerPos;
flat in int matId;

#ifdef G_WATERPASS
/* RENDERTARGETS: 0,1,2,3,5 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outMaterial;
layout(location = 3) out vec4 outWater;
layout(location = 4) out vec4 outWaterTint;
#else
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
#endif

void main() {
	vec4 tex = texture(gtexture, texcoord);
	vec4 albedo = tex * glcolor;
	vec2 lm = lightmapLevels(lmcoord);
	vec3 V = -normalize(playerPos);

#ifdef G_WEATHER
	if (albedo.a < 0.02) discard;
	vec3 rc = (ambientCol * 0.9 + lightCol * 0.12) * lm.y + BLOCK_LIGHT * lm.x * 0.3;
	outColor = vec4(rc * 0.9, albedo.a * 0.35);
	return;
#endif
#ifdef G_GLINT
	outColor = vec4(toLinear(albedo.rgb) * 1.2, albedo.a);
	return;
#endif

#ifdef G_WATERPASS
	if (matId == MAT_WATER) {
		vec3 wp = playerPos + cameraPosition;
		vec3 N = normalW.y > 0.5 ? waveNormal(wp.xz, frameTimeCounter) : normalize(normalW);
		vec2 g;
		float hgt = waveHeight(wp.xz, frameTimeCounter, g);
		// water depth below this point (opaque depth minus surface depth, along the view ray)
		vec2 suv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
		float dOpaque = linearDepth(texture(depthtex1, suv).r, near, far);
		float dSurf = linearDepth(gl_FragCoord.z, near, far);
		float wDepth = max(dOpaque - dSurf, 0.0) * saturate(abs(V.y) + 0.15);
		vec3 shadow = vec3(1.0);
		float NdotL = dot(N, lightDirW);
		if (lm.y > 0.0) shadow = sampleShadow(playerPos, vec3(0.0, 1.0, 0.0), saturate(lightDirW.y), gl_FragCoord.xy) * smoothstep(0.0, 0.2, lm.y);
		// sun glitter: a very smooth lobe on top of fine sparkle normals
		vec3 Ns = normalize(N + vec3(vnoise(wp.xz * 9.0 + frameTimeCounter * 2.0) - 0.5, 0.0, vnoise(wp.zx * 9.0 - frameTimeCounter * 1.7) - 0.5) * 0.18);
		vec3 H = normalize(V + lightDirW);
		float spec = pow(saturate(dot(Ns, H)), 900.0) * 140.0 + pow(saturate(dot(N, H)), 180.0) * 6.0;
		vec3 glitter = lightCol * shadow * spec * 0.02 * saturate(NdotL * 4.0);
		float foam = 0.0;
#ifdef WATER_FOAM
		float n1 = vnoise(wp.xz * 1.3 + vec2(frameTimeCounter * 0.35, 0.0));
		float n2 = vnoise(wp.xz * 3.7 - vec2(0.0, frameTimeCounter * 0.5));
		float shore = smoothstep(1.1, 0.0, wDepth) * smoothstep(0.35, 0.65, n1 * 0.6 + n2 * 0.4 + 0.25 * sin(wDepth * 6.0 - frameTimeCounter * 1.6));
		float caps = smoothstep(0.075, 0.11, hgt) * smoothstep(0.55, 0.8, n2) * WATER_WAVES;
		foam = saturate(shore + caps * 0.7) * step(0.5, normalW.y);
#endif
		vec3 foamCol = vec3(0.9) * (lightCol * shadow * saturate(lightDirW.y + 0.2) + ambientCol * lm.y + BLOCK_LIGHT * lm.x * 0.5);
		// depth colour: the biome water tint (Leonida biomes: turquoise, teal, olive) steers the deep colour
		vec3 tint = toLinear(glcolor.rgb);
		vec3 deep = mix(vec3(0.04, 0.15, 0.17), tint * vec3(0.10, 0.30, 0.34) / max(luma(tint), 0.05), 0.35);
		outColor = vec4(0.0);
		outNormal = vec4(encodeNormal(N), lm);
		outMaterial = vec4(float(MAT_WATER) / 255.0, 0.97, 0.02, 0.0);
		outWater = vec4(glitter * (1.0 - foam) + foamCol * foam, foam);
		outWaterTint = vec4(deep, 1.0);
		return;
	}
#endif

	if (albedo.a < 0.02) discard;
	Surface s;
	s.albedo = toLinear(albedo.rgb);
	s.N = normalize(normalW);
	s.lm = lm;
	s.mat = matId;
	s.smoothness = (matId == MAT_GLASS || matId == MAT_TOWER_GLASS || matId == MAT_WINDOW) ? 0.95 : 0.0;
	s.f0 = 0.04;
	s.emissive = 0.0;
	// fullbright glow overlays on windows if they land in the translucent pass
	if ((matId == MAT_WINDOW || matId == MAT_TOWER_GLASS) && lm.x > 0.97) s.albedo *= litWindowTint(playerPos + cameraPosition, s.N);
#ifdef G_FWD_ENTITY
	s.albedo = mix(s.albedo, toLinear(entityColor.rgb), entityColor.a);
#endif
#ifdef G_TEXTURED
	// particles: flat lit, no normal
	s.N = vec3(0.0, 1.0, 0.0);
	vec3 pc = s.albedo * (lightCol * 0.6 * smoothstep(0.0, 0.3, lm.y) + ambientCol * lm.y * lm.y + BLOCK_LIGHT * (pow(lm.x, 4.0) * 2.4 + 0.05));
	outColor = vec4(pc, albedo.a);
	return;
#endif
	vec3 shadow;
	g_emitScale = mix(0.32, 1.0, smoothstep(-0.10, 0.25, sunDirW.y));
	g_dayF = smoothstep(-0.10, 0.25, sunDirW.y);
	vec3 c = shadeSurface(s, playerPos, V, lightDirW, lightCol, ambientCol, gl_FragCoord.xy, shadow);
	float a = albedo.a;
#ifdef G_WATERPASS
	// glass: thin tinted pane, more opaque at grazing angles
	if (matId == MAT_GLASS || matId == MAT_TOWER_GLASS || matId == MAT_WINDOW) a = mix(a, 1.0, pow5(1.0 - saturate(dot(s.N, V))) * 0.5);
	outColor = vec4(c, a);
	outNormal = vec4(encodeNormal(s.N), lm);
	outMaterial = vec4(float(matId) / 255.0, s.smoothness, s.f0, 0.0);
	outWater = vec4(0.0);
	outWaterTint = vec4(0.0);
#else
	outColor = vec4(c, a);
#endif
}
