#version 330 compatibility
// Leonida Shaders: screen space reflections (water, glass towers, windows, wet asphalt, metal), then the grey-blue
// distance haze that turns gold towards the sun at golden hour, and underwater fog.
#include "/lib/common.glsl"
#include "/lib/atmosphere.glsl"
#include "/lib/sky.glsl"
#include "/program/frame.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex5;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float near, far;
uniform float rainStrength;
uniform int isEyeInWater;
uniform int frameCounter;
#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
uniform float dhFarPlane;
uniform int dhRenderDistance;
uniform float viewWidth;
uniform float viewHeight;
#endif

in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 skyForReflection(vec3 rdW) {
	vec3 c = skyRadiance(colortex6, rdW, sunDirW);
#ifdef CLOUDS
	vec3 horizon = skyRadiance(colortex6, normalize(vec3(rdW.x, 0.03, rdW.z)), sunDirW);
	vec4 cl = cumulus(rdW, sunDirW, cameraPosition, frameTimeCounter, sunColW, ambientCol, horizon);
	c = c * cl.a + cl.rgb;
#endif
	return c;
}

#ifdef SSR
// march the reflected ray through the opaque depth buffer; returns rgb and hit confidence
vec4 traceReflection(vec3 viewPos, vec3 R, float jitter) {
	float stepLen = max(0.12, -viewPos.z * 0.018);
	vec3 p = viewPos;
	p += R * stepLen * jitter;
	for (int i = 0; i < SSR_STEPS; i++) {
		p += R * stepLen;
		vec3 sc = viewToScreen(p, gbufferProjection);
		if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0 || sc.z > 1.0 || p.z > -near) break;
		float d = texture(depthtex1, sc.xy).r;
		if (sc.z > d && d < 1.0) {
			float sceneZ = linearDepth(d, near, far);
			float rayZ = -p.z;
			if (rayZ - sceneZ < stepLen * 2.5 + 0.4) {
				// refine
				vec3 a = p - R * stepLen, b = p;
				for (int j = 0; j < 5; j++) {
					vec3 m = (a + b) * 0.5;
					vec3 ms = viewToScreen(m, gbufferProjection);
					if (ms.z > texture(depthtex1, ms.xy).r) b = m; else a = m;
				}
				vec3 hs = viewToScreen(b, gbufferProjection);
				vec2 edge = smoothstep(0.0, 0.08, hs.xy) * smoothstep(1.0, 0.92, hs.xy);
				return vec4(texture(colortex0, hs.xy).rgb, edge.x * edge.y);
			}
		}
		stepLen *= 1.22;
	}
	return vec4(0.0);
}
#endif

void main() {
	vec3 col = texture(colortex0, texcoord).rgb;
	float depth = texture(depthtex0, texcoord).r;
	vec3 viewPos = screenToView(vec3(texcoord, depth), gbufferProjectionInverse);
	vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos;
	vec3 rdW = normalize(playerPos);
	bool isDH = false;
	float fogFar = far;
	if (depth >= 1.0) {
#ifdef DISTANT_HORIZONS
		float dhDepth = texture(dhDepthTex0, texcoord).r;
		if (dhDepth < 1.0 && dot(texture(colortex1, texcoord), vec4(1.0)) > 0.0) {
			viewPos = screenToView(vec3(texcoord, dhDepth), dhProjectionInverse);
			playerPos = mat3(gbufferModelViewInverse) * viewPos;
			rdW = normalize(playerPos);
			isDH = true;
		} else
#endif
		{ outColor = vec4(col, 1.0); return; }
	}
#ifdef DISTANT_HORIZONS
	if (isDH) {
		// LOD cells are big: soften the hard rectangular edges where LOD water meets LOD land by averaging with
		// LOD neighbours across a material change (never with near chunks, whose colour here is pre-water)
		vec2 px = max(1.5, viewHeight / 320.0) / vec2(viewWidth, viewHeight);
		int m0 = int(texture(colortex2, texcoord).r * 255.0 + 0.5);
		vec3 acc = col;
		float n = 1.0;
		bool edge = false;
		const vec2 taps[8] = vec2[8](vec2(1, 0), vec2(-1, 0), vec2(0, 1), vec2(0, -1), vec2(1, 1), vec2(-1, 1), vec2(1, -1), vec2(-1, -1));
		for (int i = 0; i < 8; i++) {
			vec2 uv = texcoord + taps[i] * px * (i < 4 ? 2.0 : 1.4);
			if (texture(depthtex0, uv).r < 1.0 || texture(dhDepthTex0, uv).r >= 1.0) continue;
			int m = int(texture(colortex2, uv).r * 255.0 + 0.5);
			edge = edge || ((m == MAT_WATER) != (m0 == MAT_WATER));
			acc += texture(colortex0, uv).rgb;
			n += 1.0;
		}
		if (edge) col = acc / n;
	}
	// with LODs the fog and the sky blend move out to the LOD edge
	fogFar = max(far, float(dhRenderDistance));
#endif

	vec4 c1 = texture(colortex1, texcoord);
	vec4 c2 = texture(colortex2, texcoord);
	int mat = int(c2.r * 255.0 + 0.5);
	float smoothness = c2.g;
	float f0 = c2.b;
	vec3 N = decodeNormal(c1.rg);
	float skyL = c1.a;
	vec3 waterAdd = vec3(0.0);
	float foam = 0.0;
	float shallow = 0.0;
	if (!isDH && mat == MAT_WATER && isEyeInWater == 0) {
		// refraction, then depth colour: red goes first, shallows read turquoise over sand, deep water teal-blue
		float dSurf = linearDepth(depth, near, far);
		vec2 ruv = texcoord + N.xz * 0.035 / (1.0 + dSurf * 0.06);
		float dr = texture(depthtex1, ruv).r;
		if (dr < depth) { ruv = texcoord; dr = texture(depthtex1, texcoord).r; }
		vec3 under = texture(colortex0, ruv).rgb;
		float path = dr >= 1.0 ? 300.0 : max(linearDepth(dr, near, far) - dSurf, 0.0);
		vec3 T = exp(-vec3(0.42, 0.10, 0.085) * path);
		vec3 deep = texture(colortex5, texcoord).rgb;
		float toSun = pow(saturate(dot(rdW, sunDirW)), 4.0);
		vec3 scatter = deep * (ambientCol * 1.3 + lightCol * 0.30 * saturate(lightDirW.y + 0.1)) * (0.25 + 0.75 * skyL)
			+ vec3(0.05, 0.25, 0.22) * sunColW * toSun * 0.05;
		col = under * T + scatter * (1.0 - T);
		// Vice City shallows: lit turquoise over sand, fading out with depth
		shallow = exp(-path * 0.22) * (1.0 - step(299.0, path));
		vec3 turq = vec3(0.10, 0.62, 0.58) * (ambientCol * 1.6 + lightCol * 0.45 * saturate(lightDirW.y + 0.1)) * (0.3 + 0.7 * skyL);
		col = mix(col, max(col, turq), shallow * 0.75);
		vec4 w = texture(colortex3, texcoord);
		foam = w.a;
		col *= 1.0 - foam;
		waterAdd = w.rgb;
	}

#ifdef SSR
	bool reflective = !isDH && smoothness > 0.35 && mat != MAT_ENTITY && mat != MAT_HAND && mat != MAT_NEON;
	if (reflective) {
		vec3 V = -rdW;
		float NdotV = saturate(dot(N, V));
		float baseF0 = f0 > 0.9 ? 0.6 : max(f0, 0.02);
		float F = baseF0 + (1.0 - baseF0) * pow5(1.0 - NdotV);
		F *= smoothstep(0.35, 0.85, smoothness) * (1.0 - foam);
		// wave slopes average the grazing Fresnel down; keeps the sea turquoise instead of a white sky mirror
		if (mat == MAT_WATER) F = min(F, 0.62) * (1.0 - 0.45 * shallow);
		float rough = pow2(1.0 - smoothness);
		float n1 = ign(gl_FragCoord.xy + float(frameCounter % 64) * 5.588);
		float n2 = ign(gl_FragCoord.yx * 1.37 + float(frameCounter % 64) * 3.17);
		// small jitter only: without temporal accumulation large jitter shows as glitter on rough wet asphalt
		vec3 Nr = normalize(N + (vec3(n1, n2, fract(n1 + n2)) - 0.5) * rough * 0.12);
		F *= mix(1.0, 0.35, saturate(rough * 3.0));
		vec3 Rw = reflect(rdW, Nr);
		if (Rw.y < 0.0 && mat == MAT_WATER) Rw.y = -Rw.y * 0.3;
		vec3 Rv = mat3(gbufferModelView) * Rw;
		vec4 hit = traceReflection(viewPos + mat3(gbufferModelView) * N * 0.05, Rv, n1);
		vec3 fallback = skyForReflection(Rw) * smoothstep(0.35, 0.9, skyL);
		fallback = max(fallback, col * 0.15 * (1.0 - smoothstep(0.35, 0.9, skyL)));
		vec3 refl = mix(fallback, hit.rgb, hit.a);
		if (f0 > 0.9) refl *= toLinear(vec3(0.85));
		if (mat == MAT_WATER) refl *= vec3(0.86, 0.97, 0.97);
		col = mix(col, refl, F);
	}
#endif
	col += waterAdd;

	float dist = length(playerPos);
	if (isEyeInWater == 1) {
		vec3 wf = vec3(0.02, 0.12, 0.14) * (ambientCol + lightCol * 0.25);
		col = mix(col, wf, 1.0 - exp(-dist * 0.07));
	} else {
		float worldY = playerPos.y + cameraPosition.y;
		float hfac = exp(-max(min(worldY, cameraPosition.y + 40.0) - 70.0, 0.0) / 140.0);
		// camFac: 1 at street level, about 0.37 at y 150, 0.07 at y 250, so the city stays clear from the air
		float camFac = exp(-max(cameraPosition.y - 90.0, 0.0) / 60.0);
		// fog starts in the outer part of the render distance (nearer in rain)
		float startD = fogFar * mix(0.82, 0.45, camFac) * (1.0 - 0.8 * rainStrength);
		float density = 0.0042 * (far / fogFar) * HAZE_DENSITY * hfac * mix(0.3, 1.0, camFac) * (1.0 + 2.5 * rainStrength);
		float fogAmt = 1.0 - exp(-max(dist - startD, 0.0) * density);
		vec3 fd = normalize(vec3(rdW.x, max(rdW.y, 0.0) * 0.5 + 0.03, rdW.z));
		vec3 fogCol = skyRadiance(colortex6, fd, sunDirW);
		fogCol += sunColW * pow(saturate(dot(rdW, sunDirW)), 6.0) * 0.25 * (1.0 - rainStrength);
		col = mix(col, fogCol, fogAmt);
		// blend the end of the render distance into the sky (horizontal distance, so looking down is clear)
		float hdist = length(playerPos.xz);
		float border = smoothstep(fogFar * mix(0.9, 0.75, camFac), fogFar * 0.99, hdist);
		col = mix(col, skyRadiance(colortex6, rdW, sunDirW), border);
	}
	outColor = vec4(col, 1.0);
}
