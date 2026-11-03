// Leonida Shaders: Distant Horizons water. Lit in place: teal depth colour, sky reflection with a capped Fresnel
// like near water, and the sun glint. Composite adds the haze. Original code, GPL-3.0-or-later.
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/program/frame.glsl"
#include "/program/dh_ids.glsl"
#include "/program/dh_water_shade.glsl"


uniform float rainStrength;

uniform float far;

in vec4 glcolor;
in vec2 lmcoord;
in vec3 normalW;
in vec3 playerPos;
flat in int dhMat;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
	// never draw LODs inside the loaded vanilla chunks: DH writes the same G-buffer after vanilla terrain, so an
	// LOD fragment there would replace the colour of real blocks (landmarks "vanished" at 100 to 150 m)
	if (far > 0.0 && length(playerPos.xz) < far - 16.0) discard;
	vec2 lm = lightmapLevels(lmcoord);
	lm.y = max(lm.y, 0.6);
	vec3 V = -normalize(playerPos);
	vec3 N = dot(normalW, normalW) > 1e-6 ? normalize(normalW) : vec3(0.0, 1.0, 0.0);
	vec3 albedo = toLinear(max(glcolor.rgb, vec3(0.06)));
	if (dhMat == DH_BLOCK_WATER) {
		outColor = vec4(dhWaterShade(albedo, N, V, lm, rainStrength), 0.94);
	} else {
		// other translucent LODs (glass): tinted and dim
		vec3 c = albedo * (ambientCol * lm.y + lightCol * 0.3 * saturate(dot(N, lightDirW)));
		outColor = vec4(c, glcolor.a);
	}
}
