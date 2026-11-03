// Leonida Shaders: physically inspired sky. Single scattering through a Rayleigh + Mie + ozone atmosphere,
// evaluated into a small sky-view table (colortex6) by prepare.fsh, then art directed for Leonida: humid haze,
// gold at golden hour, pink and purple after sunset, deep navy night. Original code, GPL-3.0-or-later.

const float R_GROUND = 6360.0e3;
const float R_TOP = 6460.0e3;
const vec3 BETA_R = vec3(5.802e-6, 13.558e-6, 33.1e-6);
const float H_R = 8000.0;
const float BETA_M_SCA = 7.0e-6;   // humid subtropical air: about twice a clear sky
const float BETA_M_EXT = 8.4e-6;
const float H_M = 1300.0;
const vec3 BETA_O = vec3(0.650e-6, 1.881e-6, 0.085e-6);
const float CAMERA_ALT = 120.0;
const float SKY_LUT_W = 192.0;
const float SKY_LUT_H = 108.0;

vec3 atmoExtinction(float h, out vec3 scR, out float scM) {
	float dR = exp(-h / H_R);
	float dM = exp(-h / H_M);
	float dO = max(0.0, 1.0 - abs(h - 25000.0) / 15000.0);
	scR = BETA_R * dR;
	scM = BETA_M_SCA * dM;
	return BETA_R * dR + BETA_M_EXT * dM + BETA_O * dO;
}

// distance to the far side of a sphere centred at the origin, or -1
float sphereFar(vec3 ro, vec3 rd, float r) {
	float b = dot(ro, rd);
	float c = dot(ro, ro) - r * r;
	float d = b * b - c;
	if (d < 0.0) return -1.0;
	return -b + sqrt(d);
}
float sphereNear(vec3 ro, vec3 rd, float r) {
	float b = dot(ro, rd);
	float c = dot(ro, ro) - r * r;
	float d = b * b - c;
	if (d < 0.0) return -1.0;
	float t = -b - sqrt(d);
	return t;
}

vec3 transmittanceToSun(vec3 p, vec3 sunDir) {
	if (sphereNear(p, sunDir, R_GROUND) > 0.0) return vec3(0.0);
	float tMax = sphereFar(p, sunDir, R_TOP);
	const int N = 6;
	float dt = tMax / float(N);
	vec3 od = vec3(0.0);
	vec3 scR; float scM;
	for (int i = 0; i < N; i++) {
		vec3 q = p + sunDir * (float(i) + 0.5) * dt;
		od += atmoExtinction(length(q) - R_GROUND, scR, scM) * dt;
	}
	return exp(-od);
}

float phaseRayleigh(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }
float phaseMie(float mu, float g) {
	float g2 = g * g;
	return 3.0 / (8.0 * PI) * ((1.0 - g2) * (1.0 + mu * mu)) / ((2.0 + g2) * pow(1.0 + g2 - 2.0 * g * mu, 1.5));
}

// sky radiance per unit sun illuminance in direction rd (world space, y up)
vec3 integrateSky(vec3 rd, vec3 sunDir) {
	vec3 ro = vec3(0.0, R_GROUND + CAMERA_ALT, 0.0);
	float tMax = sphereFar(ro, rd, R_TOP);
	float tGround = sphereNear(ro, rd, R_GROUND);
	if (tGround > 0.0) tMax = tGround;
	tMax = min(tMax, 400.0e3);
	const int N = 16;
	float mu = dot(rd, sunDir);
	float pR = phaseRayleigh(mu);
	float pM = phaseMie(mu, 0.76);
	vec3 trans = vec3(1.0);
	vec3 sum = vec3(0.0);
	float tPrev = 0.0;
	for (int i = 0; i < N; i++) {
		// quadratic step distribution: dense near the camera where the haze lives
		float s1 = (float(i) + 1.0) / float(N);
		float t = tMax * s1 * s1;
		float dt = t - tPrev;
		float tm = tPrev + 0.5 * dt;
		tPrev = t;
		vec3 q = ro + rd * tm;
		float h = length(q) - R_GROUND;
		vec3 scR; float scM;
		vec3 ext = atmoExtinction(h, scR, scM);
		vec3 sunT = transmittanceToSun(q, sunDir);
		vec3 S = (scR * pR + scM * pM) * sunT;
		// multiple scattering stand-in: a little isotropic light from the lit sky
		S += (scR + scM) * 0.08 * (sunT + 0.15 * saturate(sunDir.y + 0.15));
		vec3 stepT = exp(-ext * dt);
		sum += trans * (S - S * stepT) / max(ext, vec3(1e-9));
		trans *= stepT;
	}
	return sum;
}

// sky-view table mapping: u = azimuth from the sun (0..pi, mirrored), v = elevation with extra horizon detail
vec2 skyLutUv(vec3 rd, vec3 sunDir) {
	float elev = asin(clamp(rd.y, -1.0, 1.0));
	float v = 0.5 + 0.5 * sign(elev) * sqrt(abs(elev) / (0.5 * PI));
	vec2 a = normalize(rd.xz + vec2(1e-6, 0.0));
	vec2 s = normalize(sunDir.xz + vec2(1e-6, 0.0));
	float az = acos(clamp(dot(a, s), -1.0, 1.0)) / PI;
	return vec2(az, v);
}
vec3 skyLutDir(vec2 uv, vec3 sunDir) {
	float e = (uv.y - 0.5) * 2.0;
	float elev = sign(e) * e * e * 0.5 * PI;
	float az = uv.x * PI;
	vec2 s = normalize(sunDir.xz + vec2(1e-6, 0.0));
	float c = cos(az), sn = sin(az);
	vec2 d = vec2(s.x * c - s.y * sn, s.x * sn + s.y * c);
	return vec3(d.x * cos(elev), sin(elev), d.y * cos(elev));
}
