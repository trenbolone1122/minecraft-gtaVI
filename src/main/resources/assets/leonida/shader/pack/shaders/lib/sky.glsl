// Leonida Shaders: sky colour, sun and moon light, clouds. Needs lib/common.glsl and lib/atmosphere.glsl.
// Original code, GPL-3.0-or-later.

// display scale: radiance shown = SKY_K * table value; sun light on surfaces = SKY_K / PI * transmittance
const float SKY_K = 9.4;
const vec3 MOON_LIGHT = vec3(0.30, 0.40, 0.62) * 0.11;
const vec3 NIGHT_AMBIENT = vec3(0.020, 0.025, 0.050);
const vec3 DUSK_PINK = vec3(0.80, 0.38, 0.48);
const vec3 DUSK_PURPLE = vec3(0.36, 0.22, 0.52);
const vec3 GOLD = vec3(1.00, 0.46, 0.20);
const vec3 CITY_GLOW = vec3(0.060, 0.032, 0.060);

float dayFactorOf(vec3 sunDir) { return smoothstep(-0.06, 0.10, sunDir.y); }
// 1 around sunset and sunrise (sun just below to just above the horizon)
float duskFactorOf(vec3 sunDir) { return smoothstep(0.16, 0.02, sunDir.y) * smoothstep(-0.24, -0.04, sunDir.y); }
// 1 while the sun is low but up: the warm "golden hour" window before sunset
float goldenFactorOf(vec3 sunDir) { return smoothstep(0.40, 0.12, sunDir.y) * smoothstep(-0.03, 0.05, sunDir.y); }
// 1 while the sun sits just above the horizon: the saturated Vice City sunset gradient
float sunsetFactorOf(vec3 sunDir) { return smoothstep(0.46, 0.08, sunDir.y) * smoothstep(-0.20, -0.05, sunDir.y); }
// keep the brightness of c but take the hue of tint
vec3 hueShift(vec3 c, vec3 tint, float amount) {
	return mix(c, tint * (luma(c) / max(luma(tint), 1e-4)), amount);
}
float nightFactorOf(vec3 sunDir) { return smoothstep(-0.02, -0.20, sunDir.y); }

vec3 sunTransmittance(vec3 sunDir) {
	return transmittanceToSun(vec3(0.0, R_GROUND + CAMERA_ALT, 0.0), normalize(sunDir + vec3(0.0, 0.004, 0.0)));
}

vec3 sampleSkyLut(sampler2D lut, vec3 rd, vec3 sunDir) {
	vec2 uv = skyLutUv(rd, sunDir);
	uv = clamp(uv, vec2(0.5 / SKY_LUT_W, 0.5 / SKY_LUT_H), vec2(1.0 - 0.5 / SKY_LUT_W, 1.0 - 0.5 / SKY_LUT_H));
	return textureLod(lut, uv, 0.0).rgb;
}

// sky radiance in direction rd without sun disc or clouds (used by fog, reflections and ambient)
vec3 skyRadiance(sampler2D lut, vec3 rd, vec3 sunDir) {
	vec3 d = rd;
	float below = saturate(-d.y * 6.0);
	d.y = max(d.y, 0.0) + 0.002;
	d = normalize(d);
	vec3 c = sampleSkyLut(lut, d, sunDir) * SKY_K;
	float up = saturate(d.y);
	float toSun = 0.5 + 0.5 * dot(normalize(rd.xz + 1e-5), normalize(sunDir.xz + 1e-5));
	// golden hour and dusk art direction from LOOK.md: gold horizon, then pink and purple after sunset
	float dusk = duskFactorOf(sunDir) * DUSK_TINT;
	vec3 duskCol = mix(DUSK_PINK, DUSK_PURPLE, smoothstep(0.02, 0.45, up));
	c += dusk * duskCol * (0.40 + 0.60 * toSun) * exp(-up * 1.5) * 0.80;
	float golden = goldenFactorOf(sunDir) * DUSK_TINT;
	c = mix(c, c * vec3(1.10, 0.90, 0.80), golden * 0.6);
	c += golden * GOLD * (0.15 + 0.85 * toSun * toSun) * exp(-up * 3.0) * 0.50;
	// sunset gradient from the thumbnail: orange at the sun, pink band, violet blue overhead
	float sunset = sunsetFactorOf(sunDir) * DUSK_TINT;
	vec3 grad = mix(vec3(0.95, 0.30, 0.40), vec3(0.30, 0.20, 0.72), smoothstep(0.06, 0.50, up));
	grad = mix(grad, GOLD, saturate(pow(toSun, 3.0) * exp(-up * 5.0) * 1.4));
	c = hueShift(c, grad, sunset * 0.85);
	// the glow around a low sun: gold, not white (ACES whitens bright pixels, so also take a little energy out)
	float halo = pow(saturate(dot(normalize(rd), sunDir)), 10.0);
	float lowSun = saturate(sunset + golden * 0.6);
	c = hueShift(c, vec3(1.0, 0.50, 0.18), halo * lowSun * 0.75);
	c *= 1.0 - 0.30 * halo * lowSun;
	// the thumbnail look: richer colour in the whole low-sun sky (applied before ACES, which desaturates)
	c = max(mix(vec3(luma(c)), c, 1.0 + 0.55 * lowSun * smoothstep(0.0, 0.25, up)), vec3(0.0));
	float night = nightFactorOf(sunDir);
	vec3 nightCol = mix(vec3(0.030, 0.030, 0.070), vec3(0.006, 0.009, 0.026), smoothstep(0.0, 0.6, up));
	nightCol += CITY_GLOW * exp(-up * 9.0);
	c = c + night * nightCol;
	// below the horizon: keep the haze colour, a little darker (sea and land far away)
	c *= 1.0 - 0.35 * below;
	return c;
}

vec3 sunLightColor(vec3 sunDir) {
	float golden = goldenFactorOf(sunDir) * DUSK_TINT;
	return sunTransmittance(sunDir) * (SKY_K / PI) * mix(vec3(1.0), vec3(1.12, 0.90, 0.68), golden);
}

// light used for shading and shadows: sun by day, moon by night, crossfaded at the horizon
vec3 mainLightColor(vec3 sunDir) {
	float day = smoothstep(-0.01, 0.06, sunDir.y);
	float night = smoothstep(0.0, -0.08, sunDir.y);
	return sunLightColor(sunDir) * day + MOON_LIGHT * night;
}

// sky irradiance on an upward facing surface, divided by PI (so diffuse = albedo * ambient)
vec3 ambientSky(sampler2D lut, vec3 sunDir) {
	vec3 s = normalize(vec3(sunDir.x, 0.0, sunDir.z) + vec3(1e-4, 0.0, 0.0));
	vec3 side = vec3(-s.z, 0.0, s.x);
	vec3 a = skyRadiance(lut, vec3(0.0, 1.0, 0.0), sunDir) * 0.30;
	a += skyRadiance(lut, normalize(s + vec3(0.0, 0.6, 0.0)), sunDir) * 0.20;
	a += skyRadiance(lut, normalize(-s + vec3(0.0, 0.6, 0.0)), sunDir) * 0.20;
	a += skyRadiance(lut, normalize(side + vec3(0.0, 0.6, 0.0)), sunDir) * 0.15;
	a += skyRadiance(lut, normalize(-side + vec3(0.0, 0.6, 0.0)), sunDir) * 0.15;
	// subtract the displayed night colour and use a calmer night ambient instead
	float night = nightFactorOf(sunDir);
	a = mix(a, NIGHT_AMBIENT, night);
	return a;
}

vec3 sunDisc(vec3 rd, vec3 sunDir) {
	float mu = dot(rd, sunDir);
	float r = 0.0105 * SUN_SIZE; // about 0.6 deg, a bit larger than real for the look
	float disc = smoothstep(cos(r * 1.15), cos(r), mu);
	vec3 t = sunTransmittance(sunDir);
	float glow = pow(saturate(mu), 900.0) * 1.5 + pow(saturate(mu), 60.0) * 0.12;
	return t * (disc * 60.0 + glow * 4.0) * step(-0.02, rd.y);
}

#ifdef CLOUDS
float cloudFbm(vec2 p) {
	float a = 0.5, s = 0.0;
	mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
	for (int i = 0; i < CLOUD_OCTAVES; i++) {
		s += a * vnoise(p);
		p = rot * p * 2.03 + 11.7;
		a *= 0.5;
	}
	return s;
}
float cloudDensity(vec2 p) {
	vec2 warp = vec2(vnoise(p * 0.5 + 3.1), vnoise(p * 0.5 + 7.7)) - 0.5;
	float n = cloudFbm(p + warp * 0.9);
	float cover = 1.0 - CLOUD_COVER;
	return smoothstep(cover * 0.86, cover * 0.86 + 0.22, n);
}
// rgb = in-scattered cloud light, a = transmittance of the sky behind
vec4 cumulus(vec3 rd, vec3 sunDir, vec3 camPos, float time, vec3 sunCol, vec3 skyAmb, vec3 horizonCol) {
	if (rd.y < 0.015) return vec4(0.0, 0.0, 0.0, 1.0);
	const float H = 900.0;
	vec2 wind = vec2(6.0, 2.0) * time;
	vec2 pm = rd.xz / rd.y * H + camPos.xz * 0.6 + wind;
	vec2 p = pm / 520.0;
	float d = cloudDensity(p);
	if (d <= 0.001) return vec4(0.0, 0.0, 0.0, 1.0);
	vec2 toSun = normalize(sunDir.xz + 1e-5) * (0.10 + 0.25 * (1.0 - saturate(sunDir.y)));
	float d2 = cloudDensity(p + toSun);
	float d3 = cloudDensity(p + toSun * 2.0);
	float lightT = exp(-(d2 + d3 * 0.6) * 2.2);
	float mu = dot(rd, sunDir);
	float silver = 1.0 + 2.5 * pow(saturate(mu), 8.0);
	vec3 lit = sunCol * lightT * silver * 0.9;
	vec3 amb = mix(skyAmb * 1.3, horizonCol * 0.9, 0.35);
	float sunset = sunsetFactorOf(sunDir) * DUSK_TINT;
	amb = hueShift(amb, DUSK_PINK, sunset * 0.7);
	vec3 col = (lit + amb) * mix(0.75, 1.0, d);
	// dusk: undersides catch the pink sky after the sun is gone
	float dusk = duskFactorOf(sunDir) * DUSK_TINT;
	col += dusk * DUSK_PINK * 0.35 * (1.0 - lightT * 0.5);
	col = hueShift(col, mix(DUSK_PINK, GOLD, lightT), sunset * 0.45);
	float fade = smoothstep(0.015, 0.18, rd.y);
	float alpha = saturate(d * 1.3) * fade;
	// far clouds dissolve into the horizon haze
	col = mix(horizonCol, col, smoothstep(0.02, 0.25, rd.y));
	return vec4(col * alpha, 1.0 - alpha);
}
#endif
