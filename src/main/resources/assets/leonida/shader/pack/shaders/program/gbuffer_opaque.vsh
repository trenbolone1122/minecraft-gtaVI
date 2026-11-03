// Leonida Shaders: opaque geometry (terrain, entities, block entities, hand, lines). Original code, GPL-3.0-or-later.
#include "/lib/common.glsl"
#include "/program/materials.glsl"

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;

in vec4 mc_Entity;
in vec4 at_tangent;
in vec2 mc_midTexCoord;

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec4 tangentW;
out vec3 playerPos;
flat out int matId;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
	glcolor = gl_Color;
	normalW = normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal));
	tangentW = vec4(normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * at_tangent.xyz)), at_tangent.w);
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	playerPos = (gbufferModelViewInverse * viewPos).xyz;
#if defined G_TERRAIN
	int id = int(mc_Entity.x + 0.5);
	matId = materialFromBlockId(id);
#ifdef WAVING_PLANTS
	bool top = texcoord.y < mc_midTexCoord.y;
	if (id == 10008 || (id == 10012 && top)) {
		vec3 wp = playerPos + cameraPosition;
		float t = frameTimeCounter * (1.4 + rainStrength);
		float amp = (id == 10008 ? 0.045 : 0.09) * (1.0 + 1.5 * rainStrength);
		vec2 sway = vec2(sin(t + wp.x * 0.7 + wp.z * 0.3), sin(t * 1.3 + wp.z * 0.6 - wp.y * 0.4)) * amp;
		sway += vec2(sin(t * 3.1 + wp.x * 2.0 + wp.y), cos(t * 2.7 + wp.z * 2.0)) * amp * 0.3;
		playerPos.xz += sway;
		viewPos = gbufferModelView * vec4(playerPos, 1.0);
	}
#endif
#elif defined G_HAND
	matId = MAT_HAND;
#elif defined G_ENTITY
	matId = MAT_ENTITY;
#else
	matId = MAT_DEFAULT;
#endif
	gl_Position = gl_ProjectionMatrix * viewPos;
}
