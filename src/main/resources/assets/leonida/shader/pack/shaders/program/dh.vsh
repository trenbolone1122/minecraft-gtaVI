// Leonida Shaders: Distant Horizons LOD geometry (dh_terrain, dh_water). Original code, GPL-3.0-or-later.
// Iris provides dhMaterialId and normalised lightmap coordinates for these programs.
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#define FRAME_VSH
#include "/program/frame.glsl"

out vec4 glcolor;
out vec2 lmcoord;
out vec3 normalW;
out vec3 playerPos;
flat out int dhMat;

void main() {
	glcolor = gl_Color;
	lmcoord = gl_MultiTexCoord1.xy;
	normalW = normalize(mat3(gbufferModelViewInverse) * (gl_NormalMatrix * gl_Normal));
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	playerPos = (gbufferModelViewInverse * viewPos).xyz;
	dhMat = dhMaterialId;
	gl_Position = gl_ProjectionMatrix * viewPos;
	computeFrame();
}
