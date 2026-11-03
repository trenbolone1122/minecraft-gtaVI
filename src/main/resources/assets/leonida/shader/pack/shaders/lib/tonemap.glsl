// Leonida Shaders: filmic tonemap. ACES approximation by Krzysztof Narkowicz (public curve fit,
// same numbers the fx fallback grade uses so shaders on and off look alike). Original code, GPL-3.0-or-later.
vec3 acesFilm(vec3 x) {
	const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
	return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

// Hue-keeping variant for bright saturated light (neon, signs, emissive blocks): per-channel ACES drives every
// channel of a bright pink or cyan towards 1.0, so it reads white. Tonemap the luminance instead, keep the
// chroma, and blend that in only where the colour is both saturated and bright.
vec3 acesFilmHue(vec3 x) {
	vec3 a = acesFilm(x);
	float mx = max(x.r, max(x.g, x.b));
	float mn = min(x.r, min(x.g, x.b));
	float L = dot(x, vec3(0.2126, 0.7152, 0.0722));
	if (mx <= 1e-4 || L <= 1e-5) return a;
	float La = acesFilm(vec3(L)).r;
	vec3 h = x * (La / L);
	h /= max(1.0, max(h.r, max(h.g, h.b)));
	float sat = (mx - mn) / mx;
	float w = smoothstep(0.35, 0.85, sat) * smoothstep(0.5, 2.0, mx);
	return mix(a, h, w);
}
