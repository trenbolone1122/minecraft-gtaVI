// Leonida Shaders: exposure. Brighter at night and indoors, like a camera adapting. GPL-3.0-or-later.
float sceneExposure(vec3 sunDir, vec2 eyeBright) {
	float day = smoothstep(-0.10, 0.25, sunDir.y);
	float e = mix(2.4, 0.62, day);
	float indoor = 1.0 - saturate(eyeBright.y / 200.0);
	e *= 1.0 + 0.8 * indoor * day;
	return e * EXPOSURE;
}
