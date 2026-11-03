#version 330 compatibility
// Leonida Shaders: shadow map. Water casts no shadow (the seabed stays lit); plants sway like in the main pass.
#include "/lib/common.glsl"
#include "/lib/shadow.glsl"

in vec4 mc_Entity;
in vec2 mc_midTexCoord;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowModelView;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;
out vec2 texcoord;
out vec4 glcolor;
flat out int blockId;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
	blockId = int(mc_Entity.x + 0.5);
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#ifdef WAVING_PLANTS
	bool top = texcoord.y < mc_midTexCoord.y;
	if (blockId == 10008 || (blockId == 10012 && top)) {
		vec3 pp = (shadowModelViewInverse * viewPos).xyz;
		vec3 wp = pp + cameraPosition;
		float t = frameTimeCounter * (1.4 + rainStrength);
		float amp = (blockId == 10008 ? 0.045 : 0.09) * (1.0 + 1.5 * rainStrength);
		vec2 sway = vec2(sin(t + wp.x * 0.7 + wp.z * 0.3), sin(t * 1.3 + wp.z * 0.6 - wp.y * 0.4)) * amp;
		sway += vec2(sin(t * 3.1 + wp.x * 2.0 + wp.y), cos(t * 2.7 + wp.z * 2.0)) * amp * 0.3;
		pp.xz += sway;
		viewPos = shadowModelView * vec4(pp, 1.0);
	}
#endif
	vec4 clip = gl_ProjectionMatrix * viewPos;
	clip.xyz = distortShadow(clip.xyz);
	if (blockId == 10001) clip = vec4(10.0, 10.0, 10.0, 1.0);
	gl_Position = clip;
}
