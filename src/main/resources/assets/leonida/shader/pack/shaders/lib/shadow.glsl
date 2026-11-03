// Leonida Shaders: shadow map distortion and soft sampling. Original code, GPL-3.0-or-later.
const float SHADOW_DISTORT = 0.86;
const float SHADOW_ZSCALE = 0.25;

vec3 distortShadow(vec3 p) {
	float d = length(p.xy) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT);
	return vec3(p.xy / d, p.z * SHADOW_ZSCALE);
}
float shadowDistortFactor(vec2 p) { return length(p) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT); }

#ifdef SHADOW_SAMPLING
uniform sampler2DShadow shadowtex0;
uniform sampler2DShadow shadowtex1;
uniform sampler2D shadowcolor0;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

// golden angle spiral, rotated per pixel
vec2 spiralTap(int i, int n, float rot) {
	float r = sqrt((float(i) + 0.5) / float(n));
	float a = float(i) * 2.39996323 + rot * TAU;
	return vec2(cos(a), sin(a)) * r;
}

// playerPos: position relative to the camera in world axes; nWorld: geometric normal
vec3 sampleShadow(vec3 playerPos, vec3 nWorld, float NdotL, vec2 fragCoord) {
#ifndef SHADOWS
	return vec3(1.0);
#else
	float dist = length(playerPos);
	if (dist > shadowDistance) return vec3(1.0);
	// normal offset bias grows with distance and grazing angle
	float bias = 0.03 + dist * 0.0018;
	vec3 p = playerPos + nWorld * bias * (1.0 + 2.0 * (1.0 - saturate(NdotL)));
	vec4 sp = shadowProjection * (shadowModelView * vec4(p, 1.0));
	float df = shadowDistortFactor(sp.xy);
	vec3 s = distortShadow(sp.xyz) * 0.5 + 0.5;
	s.z -= 0.00012 * df;
	float radius = SHADOW_SOFTNESS * 1.6 / float(shadowMapResolution) / df;
	float rot = ign(fragCoord);
	float lit0 = 0.0, lit1 = 0.0;
	vec3 tint = vec3(0.0);
	for (int i = 0; i < SHADOW_SAMPLES; i++) {
		vec2 o = spiralTap(i, SHADOW_SAMPLES, rot) * radius;
		vec3 q = vec3(s.xy + o, s.z);
		float a = texture(shadowtex0, q);
		lit0 += a;
#ifdef COLORED_SHADOWS
		float b = texture(shadowtex1, q);
		lit1 += b;
		if (b > a) {
			vec4 c = texture(shadowcolor0, q.xy);
			tint += toLinear(c.rgb) * (1.0 - c.a * 0.5) * (b - a);
		}
#endif
	}
	float n = float(SHADOW_SAMPLES);
	vec3 result = vec3(lit0 / n);
#ifdef COLORED_SHADOWS
	result += tint / n;
#endif
	// fade out at the shadow distance edge
	float fade = smoothstep(shadowDistance * 0.8, shadowDistance, dist);
	return mix(result, vec3(1.0), fade);
#endif
}
#endif
