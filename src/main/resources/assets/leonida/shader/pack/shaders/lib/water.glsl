// Leonida Shaders: water surface. Sum of directional swells from the east (LOOK.md: Gerstner swell from the east)
// plus small chop. Returns height and its gradient. Original code, GPL-3.0-or-later.

float waveHeight(vec2 p, float t, out vec2 grad) {
	grad = vec2(0.0);
	float h = 0.0;
	// direction (x east, z south), wavelength, amplitude, speed
	const int N = 6;
	vec4 w[6] = vec4[6](
		vec4(-1.0, 0.25, 9.0, 0.060),
		vec4(-0.8, -0.55, 5.3, 0.040),
		vec4(-0.6, 0.80, 3.1, 0.025),
		vec4(0.3, 1.0, 1.7, 0.014),
		vec4(-1.0, -0.2, 1.1, 0.009),
		vec4(0.7, -0.7, 0.7, 0.006)
	);
	for (int i = 0; i < N; i++) {
		vec2 dir = normalize(w[i].xy);
		float k = TAU / w[i].z;
		float c = sqrt(9.8 / k);
		float ph = k * (dot(dir, p) + c * t * 0.6) + float(i) * 1.7;
		float a = w[i].w * WATER_WAVES;
		// sharper crests: (sin+1)^2 shape
		float s = sin(ph) * 0.5 + 0.5;
		h += a * (s * s * 2.0 - 0.5);
		grad += a * 2.0 * s * cos(ph) * k * dir;
	}
	return h;
}

vec3 waveNormal(vec2 p, float t) {
	vec2 g;
	waveHeight(p, t, g);
	// fine ripple detail from value noise
	float e = 0.15;
	float r0 = vnoise(p * 2.3 + t * vec2(0.6, 0.2));
	float rx = vnoise((p + vec2(e, 0.0)) * 2.3 + t * vec2(0.6, 0.2));
	float rz = vnoise((p + vec2(0.0, e)) * 2.3 + t * vec2(0.6, 0.2));
	g += vec2(rx - r0, rz - r0) / e * 0.05 * WATER_WAVES;
	return normalize(vec3(-g.x, 1.0, -g.y));
}
