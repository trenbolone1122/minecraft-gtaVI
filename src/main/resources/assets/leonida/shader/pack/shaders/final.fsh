#version 330 compatibility
// Leonida Shaders: bloom, exposure, filmic tonemap, colour grade, vignette.
#include "/lib/common.glsl"
#include "/lib/exposure.glsl"
#include "/lib/tonemap.glsl"

/*
const bool colortex4MipmapEnabled = true;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex4;
uniform vec3 sunPosition;
uniform mat4 gbufferModelViewInverse;
uniform ivec2 eyeBrightnessSmooth;
uniform float viewWidth, viewHeight;
in vec2 texcoord;

layout(location = 0) out vec4 fragColor;

vec3 bloomLevel(float lod) {
	vec2 px = exp2(lod) / vec2(viewWidth, viewHeight);
	vec3 s = textureLod(colortex4, texcoord, lod).rgb * 4.0;
	s += textureLod(colortex4, texcoord + vec2(px.x, 0.0), lod).rgb * 2.0;
	s += textureLod(colortex4, texcoord - vec2(px.x, 0.0), lod).rgb * 2.0;
	s += textureLod(colortex4, texcoord + vec2(0.0, px.y), lod).rgb * 2.0;
	s += textureLod(colortex4, texcoord - vec2(0.0, px.y), lod).rgb * 2.0;
	s += textureLod(colortex4, texcoord + px, lod).rgb;
	s += textureLod(colortex4, texcoord - px, lod).rgb;
	s += textureLod(colortex4, texcoord + vec2(px.x, -px.y), lod).rgb;
	s += textureLod(colortex4, texcoord + vec2(-px.x, px.y), lod).rgb;
	return s / 16.0;
}

void main() {
	vec3 sunDir = normalize(mat3(gbufferModelViewInverse) * sunPosition);
	vec3 c = texture(colortex0, texcoord).rgb * sceneExposure(sunDir, vec2(eyeBrightnessSmooth));
#ifdef BLOOM
	vec3 b = bloomLevel(1.0) * 0.30 + bloomLevel(2.0) * 0.25 + bloomLevel(3.0) * 0.20 + bloomLevel(4.0) * 0.15
		+ bloomLevel(5.0) * 0.12 + bloomLevel(6.0) * 0.10;
	c += b * 0.36 * BLOOM_STRENGTH;
#endif
	c = acesFilmHue(c);
	float l = luma(c);
	c = max(mix(vec3(l), c, SATURATION), 0.0);
	vec2 v = texcoord - 0.5;
	c *= 1.0 - VIGNETTE * dot(v, v) * 2.2;
	c = toSrgb(c);
	c += (ign(gl_FragCoord.xy) - 0.5) / 255.0;
	fragColor = vec4(c, 1.0);
}
