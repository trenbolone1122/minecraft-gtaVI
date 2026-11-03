#version 330 compatibility
// Leonida Shaders: lights the opaque scene (sun and moon with soft shadows, sky fill, block light, emissives),
// draws the sky, sun disc and cumulus, and adds the rim light on characters.
#define SHADOW_SAMPLING
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/lib/shadow.glsl"
#include "/lib/lighting.glsl"
#include "/program/frame.glsl"
#include "/program/dh_water_shade.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16;
const int colortex2Format = RGBA8;
const int colortex3Format = RGBA16F;
const int colortex4Format = R11F_G11F_B10F;
const int colortex5Format = R11F_G11F_B10F;
const int colortex6Format = RGBA16F;
const vec4 colortex0ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex1ClearColor = vec4(0.5, 1.0, 0.0, 1.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex6Clear = false;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferModelView;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float viewWidth, viewHeight;
uniform float near, far;
uniform float rainStrength;
#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex1;
uniform mat4 dhProjectionInverse;
#endif

in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 renderSky(vec3 rdW, vec3 stars) {
	vec3 c = skyRadiance(colortex6, rdW, sunDirW);
	vec3 horizon = skyRadiance(colortex6, normalize(vec3(rdW.x, 0.03, rdW.z)), sunDirW);
	c += sunDisc(rdW, sunDirW) * (1.0 - rainStrength);
	// stars and the moon come from the vanilla sky passes in colortex0
	c += toLinear(stars) * (0.6 + 2.5 * nightFactorOf(sunDirW));
#ifdef CLOUDS
	vec4 cl = cumulus(rdW, sunDirW, cameraPosition, frameTimeCounter, sunColW, ambientCol, horizon);
	c = c * cl.a + cl.rgb;
#endif
	return c;
}

void main() {
	float depth = texture(depthtex0, texcoord).r;
	vec3 viewPos = screenToView(vec3(texcoord, depth), gbufferProjectionInverse);
	vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos;
	vec3 rdW = normalize(playerPos);
	vec4 c0 = texture(colortex0, texcoord);
	bool isDH = false;
	if (depth >= 1.0) {
#ifdef DISTANT_HORIZONS
		// Distant Horizons LOD terrain lives in its own depth buffer
		float dhDepth = texture(dhDepthTex1, texcoord).r;
		// only trust DH depth where dh_terrain really wrote the G-buffer this frame: a depth texel without
		// G-buffer data (stale or offset while moving) was shaded from the empty buffer and came out black
		if (dhDepth < 1.0 && dot(texture(colortex1, texcoord), vec4(1.0)) > 0.0) {
			viewPos = screenToView(vec3(texcoord, dhDepth), dhProjectionInverse);
			playerPos = mat3(gbufferModelViewInverse) * viewPos;
			rdW = normalize(playerPos);
			isDH = true;
		} else
#endif
		{
			outColor = vec4(renderSky(rdW, c0.rgb), 1.0);
			return;
		}
	}
	vec4 c1 = texture(colortex1, texcoord);
	vec4 c2 = texture(colortex2, texcoord);
	Surface s;
	s.albedo = toLinear(c0.rgb);
	s.N = decodeNormal(c1.rg);
	s.lm = c1.ba;
	s.mat = int(c2.r * 255.0 + 0.5);
	s.smoothness = c2.g;
	s.f0 = c2.b;
	s.emissive = c2.a;
	vec3 V = -rdW;
	if (isDH && s.mat == MAT_WATER) {
		outColor = vec4(dhWaterShade(s.albedo, s.N, V, s.lm, rainStrength), 1.0);
		return;
	}
	vec3 shadow;
	g_emitScale = mix(0.32, 1.0, smoothstep(-0.10, 0.25, sunDirW.y));
	g_dayF = smoothstep(-0.10, 0.25, sunDirW.y);
	vec3 col = shadeSurface(s, playerPos, V, lightDirW, lightCol, ambientCol, gl_FragCoord.xy, shadow);

#ifdef RIM_LIGHT
	if (s.mat == MAT_ENTITY) {
		// silhouette edge on the side facing the sun or moon: look one step towards the light in screen space
		vec3 lv = mat3(gbufferModelView) * lightDirW;
		vec2 ls = (gbufferProjection * vec4(viewPos + lv * 0.5, 1.0)).xy / (gbufferProjection * vec4(viewPos + lv * 0.5, 1.0)).w * 0.5 + 0.5;
		vec2 dir = ls - texcoord;
		dir = length(dir) > 1e-5 ? normalize(dir) : vec2(1.0, 0.0);
		vec2 px = 1.0 / vec2(viewWidth, viewHeight);
		float ld = linearDepth(depth, near, far);
		float edge = 0.0;
		float rimPx = max(2.0, viewHeight / 300.0);
		for (int i = 1; i <= 4; i++) {
			float d2 = texture(depthtex0, texcoord + dir * px * float(i) * rimPx).r;
			edge = max(edge, smoothstep(0.6, 2.5, linearDepth(d2, near, far) - ld));
		}
		float NdotV = saturate(dot(s.N, V));
		float fres = pow(1.0 - NdotV, 3.0) * saturate(dot(s.N, lightDirW) + 0.3);
		float back = 0.45 + 0.55 * saturate(dot(-V, lightDirW));
		// unshadowed light colour: the rim is the light wrapping the silhouette, it should read even in shade
		vec3 rimCol = mix(lightCol, sunColW, smoothstep(-0.05, 0.05, sunDirW.y)) * 2.0 + ambientCol * 0.8;
		col += s.albedo * 0.9 * rimCol * (edge * back + fres * 0.6) * RIM_STRENGTH * smoothstep(0.0, 0.3, s.lm.y)
			+ rimCol * edge * back * 0.15 * RIM_STRENGTH;
	}
#endif
	outColor = vec4(col, 1.0);
}
