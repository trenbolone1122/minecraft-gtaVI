// Leonida Shaders: Distant Horizons opaque LODs into the same G-buffer as near terrain, so deferred lighting,
// haze and the sky blend match the pack in every profile. Original code, GPL-3.0-or-later.
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/program/frame.glsl"
#include "/program/dh_ids.glsl"

uniform vec3 cameraPosition;

uniform float far;

in vec4 glcolor;
in vec2 lmcoord;
in vec3 normalW;
in vec3 playerPos;
flat in int dhMat;

/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outMaterial;

void main() {
	// never draw LODs inside the loaded vanilla chunks: DH writes the same G-buffer after vanilla terrain, so an
	// LOD fragment there would replace the colour of real blocks (landmarks "vanished" at 100 to 150 m)
	if (far > 0.0 && length(playerPos.xz) < far - 16.0) discard;
	// never black: DH can hand over a zero colour or zero sky light for LOD faces it has not lit yet
	vec3 albedo = max(glcolor.rgb, vec3(0.06));
	vec2 lm = lightmapLevels(lmcoord);
	lm.y = max(lm.y, 0.6);
	int mat = MAT_DEFAULT;
	float smoothness = 0.0;
	float f0 = 0.04;
	float emissive = 0.0;
	if (dhMat == DH_BLOCK_LEAVES || dhMat == DH_BLOCK_GRASS) mat = MAT_FOLIAGE;
	else if (dhMat == DH_BLOCK_SAND) mat = MAT_SAND;
	// LOD water drawn in the opaque pass (DH transparency off): deferred shades it as water
	else if (dhMat == DH_BLOCK_WATER) { mat = MAT_WATER; smoothness = 0.95; f0 = 0.02; }
	else if (dhMat == DH_BLOCK_METAL) { mat = MAT_METAL; smoothness = 0.6; f0 = 0.95; }
	else if (dhMat == DH_BLOCK_ILLUMINATED || dhMat == DH_BLOCK_LAVA) { mat = MAT_LAMP; emissive = 0.6; }
	// lit tower windows far away: bright LOD columns at night keep a little glow
	if (lm.x > 0.9 && mat == MAT_DEFAULT) emissive = 0.06;
	outAlbedo = vec4(albedo, 1.0);
	vec3 n = dot(normalW, normalW) > 1e-6 ? normalize(normalW) : vec3(0.0, 1.0, 0.0);
	outNormal = vec4(encodeNormal(n), lm);
	outMaterial = vec4(float(mat) / 255.0, smoothness, f0, emissive);
}
