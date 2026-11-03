// Leonida Shaders: translucent geometry lit in place (stained glass, particles, rain, translucent entities).
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/program/materials.glsl"
#include "/lib/water.glsl"
#define FRAME_VSH
#include "/program/frame.glsl"

uniform mat4 gbufferModelView;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
in vec4 mc_Entity;

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalW;
out vec3 playerPos;
flat out int matId;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
	glcolor = gl_Color;
	normalW = normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal));
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	playerPos = (gbufferModelViewInverse * viewPos).xyz;
#ifdef G_WATERPASS
	matId = materialFromBlockId(int(mc_Entity.x + 0.5));
	if (matId == MAT_WATER) {
		vec2 g;
		vec3 wp = playerPos + cameraPosition;
		if (normalW.y > 0.5) playerPos.y += waveHeight(wp.xz, frameTimeCounter, g) * 0.6 - 0.06 * WATER_WAVES;
		viewPos = gbufferModelView * vec4(playerPos, 1.0);
	}
#elif defined G_FWD_ENTITY
	matId = MAT_ENTITY;
#else
	matId = MAT_DEFAULT;
#endif
	gl_Position = gl_ProjectionMatrix * viewPos;
	computeFrame();
}
