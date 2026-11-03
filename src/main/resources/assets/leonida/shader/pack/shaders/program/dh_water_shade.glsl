// Leonida Shaders: shading for Distant Horizons water, shared by deferred (opaque LOD water) and dh_water
// (translucent LOD water). Matches the near water: dark teal body, capped Fresnel sky reflection, sun glint.
// Needs frame.glsl and sky.glsl first. Original code, GPL-3.0-or-later.
vec3 dhWaterShade(vec3 albedoLin, vec3 N, vec3 V, vec2 lm, float rain) {
	// keep a hint of the biome tint but pull it down to the pack's deep teal
	vec3 tint = albedoLin / max(luma(albedoLin), 0.02);
	vec3 deep = mix(vec3(0.020, 0.085, 0.095), vec3(0.020, 0.085, 0.095) * tint, 0.25);
	vec3 body = deep * (ambientCol * 1.6 + lightCol * 0.35 * saturate(lightDirW.y + 0.1)) * (0.3 + 0.7 * lm.y);
	vec3 R = reflect(-V, N);
	R.y = abs(R.y);
	float F = min(0.02 + 0.98 * pow5(1.0 - saturate(dot(N, V))), 0.62);
	vec3 refl = skyRadiance(colortex6, R, sunDirW) * mix(0.25, 1.0, smoothstep(0.35, 0.9, lm.y));
	// broad, soft glint: LOD cells are big, a tight highlight would just flicker
	float spec = pow(saturate(dot(R, lightDirW)), 90.0) * 2.5;
	return mix(body, refl, F) + lightCol * spec * saturate(lightDirW.y * 4.0) * (1.0 - rain);
}
