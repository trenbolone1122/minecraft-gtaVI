package dev.leonida.vehicle.render;

/** Small detail pieces placed on a {@link Body}: lights, bumpers, plates, mirrors, trim, police equipment. */
final class Parts {
	private Parts() {}

	/** Pair of elements on the nose from |x| = xIn to xOut, flush with the (rounded) front. */
	static void front(VehicleModel m, Body b, Mat mat, float xIn, float xOut, float y0, float y1, float proud) {
		float z = b.frontZ(xOut);
		m.pair(mat, xIn, y0, z - 0.6f, xOut, y1, z + proud);
	}

	static void rear(VehicleModel m, Body b, Mat mat, float xIn, float xOut, float y0, float y1, float proud) {
		float z = b.rearZ(xOut);
		m.pair(mat, xIn, y0, z - proud, xOut, y1, z + 0.6f);
	}

	/** Centred element on the nose, |x| < halfW. */
	static VehicleModel.Box frontC(VehicleModel m, Body b, Mat mat, float halfW, float y0, float y1, float proud) {
		float z = b.frontZ(halfW);
		return m.box(mat, -halfW, y0, z - 0.6f, halfW, y1, z + proud);
	}

	static VehicleModel.Box rearC(VehicleModel m, Body b, Mat mat, float halfW, float y0, float y1, float proud) {
		float z = b.rearZ(halfW);
		return m.box(mat, -halfW, y0, z - proud, halfW, y1, z + 0.6f);
	}

	static void headlights(VehicleModel m, Body b, Mat mat, float xIn, float xOut, float y0, float y1) {
		front(m, b, mat, xIn, xOut, y0, y1, 0.25f);
		m.beam((xIn + xOut) / 2, (y0 + y1) / 2, b.frontZ(xOut) + 0.3f);
	}

	static void tails(VehicleModel m, Body b, Mat mat, float xIn, float xOut, float y0, float y1) {
		rear(m, b, mat, xIn, xOut, y0, y1, 0.25f);
	}

	/** Wrap-around bumper: front face plus short returns along the sides. */
	static void bumperF(VehicleModel m, Body b, Mat mat, float y0, float y1, float proud) {
		float z = b.frontZ(b.hw(b.hl) - 0.5f);
		float w = b.hw(z - 1);
		m.box(mat, -w + 0.6f, y0, z - 1, w - 0.6f, y1, z + proud);
		float zs = b.hl - 6;
		m.pair(mat, b.hw(zs) - 0.2f, y0, zs, b.hw(zs) + 0.3f, y1, z - 1);
	}

	static void bumperR(VehicleModel m, Body b, Mat mat, float y0, float y1, float proud) {
		float z = b.rearZ(b.hw(-b.hl) - 0.5f);
		float w = b.hw(z + 1);
		m.box(mat, -w + 0.6f, y0, z - proud, w - 0.6f, y1, z + 1);
		float zs = -b.hl + 6;
		m.pair(mat, b.hw(zs) - 0.2f, y0, z + 1, b.hw(zs) + 0.3f, y1, zs);
	}

	static void plates(VehicleModel m, Body b, float yF, float yR) {
		if (yF > 0) frontC(m, b, Mat.PLATE, 3.4f, yF, yF + 2.2f, 0.35f);
		if (yR > 0) rearC(m, b, Mat.PLATE, 3.4f, yR, yR + 2.2f, 0.35f);
	}

	static void mirrors(VehicleModel m, Body b, Mat mat, float z, float y) {
		float w = b.hw(z) - b.gi;
		m.pair(mat, w - 0.4f, y, z - 2.6f, w + 2.0f, y + 1.8f, z - 0.6f);
		m.pair(Mat.CHROME, w + 0.5f, y + 0.2f, z - 2.65f, w + 1.9f, y + 1.6f, z - 2.55f);
	}

	static void handles(VehicleModel m, Body b, Mat mat, float y, float... zs) {
		for (float z : zs) {
			float w = b.hw(z);
			m.pair(mat, w - 0.1f, y, z - 2, w + 0.25f, y + 0.6f, z);
		}
	}

	/** Door shut line on both sides from y0 to y1 at z. */
	static void seam(VehicleModel m, Body b, float z, float y0, float y1) {
		float w = b.hw(z);
		m.pair(Mat.TRIM, w - 0.1f, y0, z - 0.15f, w + 0.06f, y1, z + 0.15f);
	}

	/** A strip on both sides that follows the plan-view width, from z0 to z1. */
	static void sideStrip(VehicleModel m, Body b, Mat mat, float y0, float y1, float z0, float z1, float out) {
		for (float z = z0; z < z1 - 1e-3f; z += 2) {
			float za = Math.min(z1, z + 2), zc = (z + za) / 2;
			float yt = b.top(zc);
			float ya = Math.min(y1, yt - 0.05f);
			if (ya <= y0) continue;
			float arch = b.arch(zc);
			float y0e = arch > 0 ? Math.max(y0, arch) : y0;
			if (ya <= y0e) continue;
			float w = b.hw(zc);
			m.pair(mat, w - 0.1f, y0e, z, w + out, ya, za);
		}
	}

	/** A strip on top of the body following the side profile (hood stripes, scoops). */
	static void onTop(VehicleModel m, Body b, Mat mat, float x0, float x1, float z0, float z1, float dy0, float dy1) {
		for (float z = z0; z < z1 - 1e-3f; z += 1) {
			float za = Math.min(z1, z + 1), zc = (z + za) / 2;
			float yt = b.top(zc);
			m.box(mat, x0, yt + dy0, z, x1, yt + dy1, za);
		}
	}

	static void exhaust(VehicleModel m, float x, float y, float z, float r, Mat mat) {
		m.box(mat, x - r, y - r * 0.6f, z - 3, x + r, y + r * 0.6f, z);
		m.box(mat, x - r * 0.6f, y - r, z - 3, x + r * 0.6f, y + r, z);
		m.box(Mat.BLACK, x - r * 0.55f, y - r * 0.55f, z - 3.05f, x + r * 0.55f, y + r * 0.55f, z - 2.9f);
	}

	static void exhaustsRear(VehicleModel m, Body b, float x, float y, float r, Mat mat, boolean pair) {
		float z = b.rearZ(Math.abs(x) + r) - 0.4f;
		exhaust(m, x, y, z + 2.2f, r, mat);
		if (pair) exhaust(m, -x, y, z + 2.2f, r, mat);
	}

	static void lightBar(VehicleModel m, float z, float roof, float halfW) {
		m.box(Mat.LIGHTBAR_BASE, -halfW, roof, z - 1.8f, halfW, roof + 0.8f, z + 1.8f);
		m.box(Mat.SIREN_RED, -halfW, roof + 0.8f, z - 1.6f, -0.8f, roof + 2.4f, z + 1.6f);
		m.box(Mat.SIREN_BLUE, 0.8f, roof + 0.8f, z - 1.6f, halfW, roof + 2.4f, z + 1.6f);
		m.box(Mat.WHITE, -0.8f, roof + 0.8f, z - 1.4f, 0.8f, roof + 2.0f, z + 1.4f);
		m.pair(Mat.BLACK, halfW - 1, roof - 0.2f, z - 1, halfW - 0.4f, roof + 0.2f, z + 1);
	}

	static void pushBar(VehicleModel m, Body b, float y0, float y1) {
		float z = b.frontZ(6) + 0.8f;
		m.box(Mat.BLACK, -6, y0, z, 6, y1, z + 1.2f);
		m.pair(Mat.BLACK, 5, y0 + 1, z - 0.6f, 6.8f, y1 + 0.5f, z + 2);
		m.box(Mat.BLACK, -5, y1 - 2, z + 0.8f, 5, y1 - 1, z + 1.6f);
		m.pair(Mat.SIREN_RED, 3.5f, y1 - 0.9f, z + 1.2f, 5, y1, z + 1.8f);
		m.box(Mat.SIREN_BLUE, -5, y1 - 0.9f, z + 1.2f, -3.5f, y1, z + 1.8f);
	}

	/** Grille-mounted and rear-window flashers for police cars. */
	static void flashers(VehicleModel m, Body b, float yF, float xF) {
		float zf = b.frontZ(xF + 1);
		m.box(Mat.SIREN_RED, xF - 1.2f, yF, zf - 0.2f, xF, yF + 0.8f, zf + 0.35f);
		m.box(Mat.SIREN_BLUE, -xF, yF, zf - 0.2f, -xF + 1.2f, yF + 0.8f, zf + 0.35f);
	}

	static void spotlight(VehicleModel m, Body b) {
		float z = b.zA - 1.5f, x = -(b.hw(z) - b.gi) - 0.3f, y = b.top(z) + 1.2f;
		m.box(Mat.CHROME, x - 0.8f, y, z - 0.8f, x + 0.6f, y + 1.4f, z + 0.9f);
		m.box(Mat.HEADLIGHT, x - 0.7f, y + 0.1f, z + 0.9f, x + 0.5f, y + 1.3f, z + 1.0f);
	}

	static void antenna(VehicleModel m, float x, float y, float z, float h) {
		m.box(Mat.BLACK, x - 0.15f, y, z - 0.15f, x + 0.15f, y + h, z + 0.15f);
	}

	/** Badge on the nose and the tail. */
	static void badges(VehicleModel m, Body b, float yF, float yR, Mat mat) {
		if (yF > 0) frontC(m, b, mat, 0.9f, yF, yF + 1, 0.4f);
		if (yR > 0) rearC(m, b, mat, 0.9f, yR, yR + 1, 0.4f);
	}

	/** Rear wing on two uprights. */
	static void wing(VehicleModel m, Body b, Mat mat, float z, float lift, float chord, float halfW) {
		float y = b.top(z) + lift;
		m.box(mat, -halfW, y, z - chord / 2, halfW, y + 0.8f, z + chord / 2);
		m.pair(mat, halfW - 0.3f, y - 1.5f, z - chord / 2, halfW + 0.3f, y + 2.2f, z + chord / 2);
		m.pair(mat, halfW * 0.55f - 0.4f, b.top(z), z - 0.8f, halfW * 0.55f + 0.4f, y, z + 0.8f);
	}

	/** Side marker lights at the corners. */
	static void markers(VehicleModel m, Body b, float y) {
		float zf = b.hl - 3.5f, zr = -b.hl + 3.5f;
		m.pair(Mat.ORANGE_LENS, b.hw(zf) - 0.1f, y, zf - 1.2f, b.hw(zf) + 0.15f, y + 0.8f, zf);
		m.pair(Mat.TAIL_PLAIN, b.hw(zr) - 0.1f, y, zr, b.hw(zr) + 0.15f, y + 0.8f, zr + 1.2f);
	}
}
