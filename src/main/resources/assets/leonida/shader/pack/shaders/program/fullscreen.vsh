out vec2 texcoord;
#ifdef FRAME
#define FRAME_VSH
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/program/frame.glsl"
#endif
void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
#ifdef FRAME
	computeFrame();
#endif
}
