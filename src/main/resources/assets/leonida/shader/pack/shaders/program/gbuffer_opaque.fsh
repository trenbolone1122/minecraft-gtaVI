// Leonida Shaders: writes albedo, normal, lightmap and material for the deferred pass. labPBR aware.
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform sampler2D normals;
uniform sampler2D specular;
uniform vec4 entityColor;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform vec3 cameraPosition;
uniform float wetness;
uniform float rainStrength;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalW;
in vec4 tangentW;
in vec3 playerPos;
flat in int matId;

/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outMaterial;

void main() {
	vec4 tex = texture(gtexture, texcoord);
#ifdef G_TERRAIN
	vec3 albedo = tex.rgb * glcolor.rgb;
	float alpha = tex.a;
	float ao = glcolor.a; // separateAo: vanilla AO in vertex alpha
#else
	vec3 albedo = tex.rgb * glcolor.rgb;
	float alpha = tex.a * glcolor.a;
	float ao = 1.0;
#endif
#ifdef G_BASIC
	albedo = glcolor.rgb;
	alpha = glcolor.a;
#endif
	if (alpha < 0.1) discard;
#if defined G_ENTITY || defined G_HAND
	albedo = mix(albedo, entityColor.rgb, entityColor.a);
#endif

	// labPBR normal map: RG = normal xy, B = AO, A = height. Iris binds a flat default when a block has none.
	vec4 nt = texture(normals, texcoord);
	vec3 N = normalize(normalW);
	if (nt.a > 0.0 || nt.r > 0.0 || nt.g > 0.0) {
		// high resolution maps (128 px) alias into sparkle when minified: fade the detail out with distance
		// also halved overall: under grazing sun, texel noise flips N.L on and off and reads as speckle
		float detail = 0.55 * (1.0 - 0.85 * smoothstep(4.0, 28.0, length(playerPos)));
		vec2 nxy = (nt.rg * 2.0 - 1.0) * detail;
		vec3 nTan = vec3(nxy, sqrt(saturate(1.0 - dot(nxy, nxy))));
		vec3 T = normalize(tangentW.xyz - N * dot(N, tangentW.xyz));
		vec3 B = cross(N, T) * (tangentW.w < 0.0 ? -1.0 : 1.0);
		N = normalize(mat3(T, B, N) * nTan);
		ao *= mix(1.0, nt.b, step(0.001, nt.b) * 0.45 * detail);
	}
	// labPBR specular: R = perceptual smoothness, G = f0 (230+ = metal), A = emission (255 = none)
	vec4 sp = texture(specular, texcoord);
	float smoothness = sp.r;
	float f0 = sp.g >= 229.5 / 255.0 ? 1.0 : sp.g;
	float emissive = sp.a < 0.999 ? sp.a * 255.0 / 254.0 : 0.0;
	bool hasPbr = sp.r > 0.0 || sp.g > 0.0 || (sp.a > 0.0 && sp.a < 0.999);

	// labPBR emission only on blocks that are lights (neon, lamps, window glow overlays): a stray emission value in
	// a wall texture's _s map must not make a solid facade glow
	if (!(matId == MAT_NEON || matId == MAT_LAMP || matId == MAT_WINDOW || matId == MAT_TOWER_GLASS || matId == MAT_GLASS)) emissive = 0.0;
	float lumaA = luma(albedo);
	vec2 lm = lightmapLevels(lmcoord);
	if (!hasPbr) {
		f0 = 0.04;
		if (matId == MAT_TOWER_GLASS) { smoothness = 0.94; f0 = 0.09; emissive = 0.3 * smoothstep(0.50, 0.80, lumaA); }
		else if (matId == MAT_WINDOW) { smoothness = 0.90; f0 = 0.06; emissive = 0.3 * smoothstep(0.50, 0.80, lumaA); }
		else if (matId == MAT_GLASS) { smoothness = 0.95; f0 = 0.04; }
		else if (matId == MAT_NEON) { smoothness = 0.6; emissive = 0.6 + 0.4 * smoothstep(0.3, 0.8, lumaA); }
		else if (matId == MAT_LAMP) { emissive = smoothstep(0.45, 0.85, lumaA); }
		else if (matId == MAT_METAL) { smoothness = 0.62; f0 = 0.5; }
		else if (matId == MAT_POLISHED) { smoothness = 0.78; }
		else { smoothness = 0.0; }
	}
	// lit office windows glow warm and soft, not white like neon
	if (matId == MAT_WINDOW || matId == MAT_TOWER_GLASS || matId == MAT_GLASS) {
		// the glow overlay quads carry full emission (labPBR A 254); the panes themselves carry none
		if (hasPbr && emissive > 0.9) albedo *= litWindowTint(playerPos + cameraPosition, normalize(normalW));
		emissive *= 0.35;
	}
#ifdef G_EMISSIVE
	emissive = 1.0;
#endif
#ifdef WET_NIGHTS
	// damp streets: puddles and a sheen at night and in rain, on upward faces of asphalt and pavement
	if (matId == MAT_ASPHALT && N.y > 0.7) {
		vec3 sd = normalize(sunPosition);
		float night = smoothstep(0.05, -0.15, dot(sd, normalize(upPosition)));
		float wet = max(wetness, night * 0.85) * WETNESS_AMOUNT * smoothstep(0.6, 0.95, lm.y);
		vec2 wp = playerPos.xz + cameraPosition.xz;
		float puddle = smoothstep(0.42, 0.62, vnoise(wp * 0.11) * 0.7 + vnoise(wp * 0.53) * 0.3);
		float w = saturate(wet * (0.45 + 0.55 * puddle));
		smoothness = mix(smoothness, 0.93, w);
		f0 = 0.02;
		albedo *= 1.0 - 0.35 * w;
		N = normalize(mix(N, normalize(normalW), w));
	}
#endif
	albedo *= ao;
	outAlbedo = vec4(albedo, 1.0);
	outNormal = vec4(encodeNormal(N), lm);
	outMaterial = vec4(float(matId) / 255.0, smoothness, f0, saturate(emissive));
}
