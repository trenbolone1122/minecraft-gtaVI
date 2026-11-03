// Leonida Shaders: per-frame lighting values, computed in vertex shaders and passed as flat varyings.
uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform mat4 gbufferModelViewInverse;
uniform sampler2D colortex6; // sky-view table from prepare.fsh

#ifdef FRAME_VSH
flat out vec3 sunDirW;
flat out vec3 lightDirW;
flat out vec3 lightCol;
flat out vec3 sunColW;
flat out vec3 ambientCol;
void computeFrame() {
	sunDirW = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	lightDirW = normalize(mat3(gbufferModelViewInverse) * shadowLightPosition);
	lightCol = mainLightColor(sunDirW);
	sunColW = sunLightColor(sunDirW);
	ambientCol = ambientSky(colortex6, sunDirW);
}
#else
flat in vec3 sunDirW;
flat in vec3 lightDirW;
flat in vec3 lightCol;
flat in vec3 sunColW;
flat in vec3 ambientCol;
#endif
