#version 330 compatibility
// Leonida Shaders: bloom bright pass into colortex4 (mipmapped and blurred in final.fsh).
#include "/lib/common.glsl"
#include "/lib/exposure.glsl"

uniform sampler2D colortex0;
uniform vec3 sunPosition;
uniform mat4 gbufferModelViewInverse;
uniform ivec2 eyeBrightnessSmooth;
in vec2 texcoord;

/* RENDERTARGETS: 4 */
layout(location = 0) out vec4 outBloom;

void main() {
	vec3 sunDir = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 c = texture(colortex0, texcoord).rgb * sceneExposure(sunDir, vec2(eyeBrightnessSmooth));
	float l = luma(c);
	float night = smoothstep(0.05, -0.15, sunDir.y);
	float T = mix(1.0, 0.55, night);
	float knee = T * 0.5;
	float soft = clamp(l - T + knee, 0.0, 2.0 * knee);
	soft = soft * soft / (4.0 * knee + 1e-4);
	float w = max(soft, l - T) / max(l, 1e-4);
	outBloom = vec4(min(c * w + c * 0.015, vec3(64.0)), 1.0);
}
