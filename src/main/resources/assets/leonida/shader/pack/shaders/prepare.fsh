#version 330 compatibility
// Leonida Shaders: fills the 192x108 sky-view table (colortex6) once per frame.
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"

uniform vec3 sunPosition;
uniform mat4 gbufferModelViewInverse;
in vec2 texcoord;

/* RENDERTARGETS: 6 */
layout(location = 0) out vec4 outSky;

void main() {
	vec2 uv = gl_FragCoord.xy / vec2(SKY_LUT_W, SKY_LUT_H);
	if (uv.x > 1.0 || uv.y > 1.0) { outSky = vec4(0.0); return; }
	vec3 sunDir = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 rd = skyLutDir(uv, sunDir);
	outSky = vec4(integrateSky(rd, sunDir), 1.0);
}
