package dev.leonida.vehicle.render;

import dev.leonida.vehicle.VehicleSpec;

/**
 * Parametric car body built from many thin cross-section slices (1 px along the length), so profiles follow the
 * real silhouette: side profile (top and bottom lines), plan view (half width), wheel arch cut-outs with flares,
 * and a sliced greenhouse whose side windows follow the windshield and rear glass slopes.
 * All values in pixels (16 px = 1 block); origin at ground centre, x right, y up, z forward.
 */
final class Body {
	final VehicleSpec spec;
	final float len, wid, hl, hwMax;
	float clear = 3.5f;
	float wheelR = 5.6f, wheelW = 4.2f, axleF, axleR, tread, archGap = 1.3f, flare = 0;
	float frontWheelR = -1;
	/** Side profile top line (z, y pairs, ascending z). */
	float[] top;
	/** Bottom line (z, y pairs); null = clear with small lifts at both ends. */
	float[] bot;
	/** Plan view half width (z, hw pairs); null = full width with rounded corners. */
	float[] wid2;
	float cornerFront = 2.5f, cornerRear = 2f;

	// greenhouse
	boolean roof = true, convertible, openTop;
	float zA, zR1, zR2, zC, roofY, crown = 0.5f;
	/** Inset of the glass from the body side at the belt, and extra inset at the roof (tumblehome). */
	float gi = 1.6f, ri = 1.6f;
	/** Side glass extends from sideRear to sideFront along z. */
	float sideFront = Float.NaN, sideRear = Float.NaN;
	/** B pillar centre z, NaN = none; and its width. */
	float bPillar = Float.NaN, bPillarW = 1.6f;
	/** Optional second side window split (vent, quarter) z, NaN = none. */
	float cSplit = Float.NaN;
	Mat lower = Mat.PAINT, upper = Mat.PAINT, roofMat = Mat.PAINT, pillar = Mat.PAINT, glass = Mat.GLASS, rearSideGlass = Mat.GLASS;
	Mat windowTrim = Mat.TRIM;
	/** Below this y the lower body uses lower2 (two-tone or livery band); 0 = off. */
	float toneY = 0, toneY2 = 0;
	Mat lower2 = Mat.ACCENT;
	boolean interior = true;
	/** Dark liner over the rear tyres; off for open beds that model their own wheel tubs. */
	boolean rearWellRoof = true;
	float seatZ, rearSeatZ = Float.NaN, seatX, seatY;
	/** Split the front doors out of the body so they can swing open. */
	boolean doors = true;
	float doorZ0 = Float.NaN, doorZ1 = Float.NaN;
	static final float DOOR_SKIN = 1.0f;

	Body(VehicleSpec spec) {
		this.spec = spec;
		len = spec.length() * 16;
		wid = spec.width() * 16;
		hl = len / 2;
		hwMax = wid / 2;
		float wb = spec.wheelbase() * 16;
		axleF = wb / 2;
		axleR = -wb / 2;
		roofY = spec.height() * 16 - 0.5f;
		seatZ = (float) spec.seats().get(0).z * 16;
		seatX = (float) Math.abs(spec.seats().get(0).x) * 16;
		seatY = (float) spec.seats().get(0).y * 16;
		if (spec.seatCount() >= 4) rearSeatZ = (float) spec.seats().get(2).z * 16;
	}

	static float lerp(float a, float b, float t) { return a + (b - a) * t; }

	static float keys(float[] k, float z) {
		if (z <= k[0]) return k[1];
		for (int i = 2; i < k.length; i += 2) {
			if (z <= k[i]) {
				float t = (z - k[i - 2]) / (k[i] - k[i - 2]);
				return lerp(k[i - 1], k[i + 1], t);
			}
		}
		return k[k.length - 1];
	}

	float top(float z) { return keys(top, z); }

	float bot(float z) {
		if (bot != null) return keys(bot, z);
		float lift = 0;
		if (z > hl - 5) lift = (z - (hl - 5)) / 5 * 2.2f;
		if (z < -hl + 5) lift = ((-hl + 5) - z) / 5 * 2.2f;
		return clear + lift;
	}

	float hw(float z) {
		float w;
		if (wid2 != null) w = keys(wid2, z);
		else {
			w = hwMax;
			float cf = cornerFront, cr = cornerRear;
			if (z > hl - cf * 2) w -= cf * sq((z - (hl - cf * 2)) / (cf * 2));
			if (z < -hl + cr * 2) w -= cr * sq(((-hl + cr * 2) - z) / (cr * 2));
		}
		if (flare > 0) {
			for (float ax : new float[] {axleF, axleR}) {
				float d = Math.abs(z - ax) / (wr(ax) + 4);
				if (d < 1) w += flare * (1 - d * d);
			}
		}
		return w;
	}

	private static float sq(float v) { return v * v; }

	float wr(float axle) { return axle == axleF && frontWheelR > 0 ? frontWheelR : wheelR; }

	float archR(float axle) { return wr(axle) + archGap; }

	/** Height of the arch opening at z, or -1 if not over a wheel. */
	float arch(float z) {
		float best = -1;
		for (float ax : new float[] {axleF, axleR}) {
			float r = archR(ax);
			float d = z - ax;
			if (Math.abs(d) < r) best = Math.max(best, wr(ax) + (float) Math.sqrt(r * r - d * d));
		}
		return best;
	}

	float inner() { return tread - wheelW / 2 - 0.5f; }

	/** Greenhouse roof line at z. */
	float roofAt(float z) {
		if (z >= zR1) return lerp(roofY, top(zA), (z - zR1) / (zA - zR1));
		if (z <= zR2) return lerp(top(zC), roofY, (z - zC) / (zR2 - zC));
		float mid = (zR1 + zR2) / 2, half = (zR1 - zR2) / 2;
		float t = half <= 0 ? 0 : (z - mid) / half;
		return roofY - crown * t * t;
	}

	/** Front-most z at which the body is at least x wide (for placing lights on rounded noses). */
	float frontZ(float x) {
		for (float z = hl; z > 0; z -= 0.25f) if (hw(z) >= x) return z;
		return hl;
	}

	float rearZ(float x) {
		for (float z = -hl; z < 0; z += 0.25f) if (hw(z) >= x) return z;
		return -hl;
	}

	void build(VehicleModel m) {
		if (tread == 0) tread = hwMax - wheelW / 2 - 0.3f;
		if (Float.isNaN(sideFront)) sideFront = zA;
		if (Float.isNaN(sideRear)) sideRear = zR2 + 2.5f;
		planDoors();
		lowerBody(m);
		wells(m);
		if (roof || convertible) greenhouse(m);
		if (interior) interior(m);
		float rf = wr(axleF), rr = wr(axleR);
		m.wheelPair(tread, rf, axleF, rf, wheelW, true, true);
		m.wheelPair(tread, rr, axleR, rr, wheelW, false, false);
		if (!Float.isNaN(doorZ0)) {
			m.doorZ0 = doorZ0;
			m.doorZ1 = doorZ1;
			float minW = hwMax, belt = 0;
			for (float z = doorZ0 + 0.5f; z < doorZ1; z += 1) {
				minW = Math.min(minW, hw(z));
				belt = Math.max(belt, top(z));
			}
			m.doorHingeX = hw(doorZ1 - 0.5f);
			m.doorInnerX = minW - 1.6f;
			m.doorY0 = doorFloor();
			m.doorY1 = belt + 1.0f;
		}
	}

	/** Front door from just behind the front arch / A pillar back to the B pillar (or behind the driver's seat). */
	private void planDoors() {
		if (!doors) return;
		float front = Math.min(Math.min(zA, sideFront) - 0.5f, axleF - archR(axleF) - 0.5f);
		float rear = !Float.isNaN(bPillar) ? bPillar - bPillarW / 2 : seatZ - (Float.isNaN(rearSeatZ) ? 9 : 7.5f);
		rear = Math.max(rear, axleR + archR(axleR) + 0.5f);
		// align to the 1 px slice grid of the lower body
		front = -hl + (float) Math.floor(front + hl);
		rear = -hl + (float) Math.ceil(rear + hl);
		if (front - rear < 7) return;
		doorZ0 = rear;
		doorZ1 = front;
	}

	boolean inDoor(float z) {
		return !Float.isNaN(doorZ0) && z > doorZ0 && z < doorZ1;
	}

	/** Top of the sill under the doors (the cabin floor). */
	float doorFloor() {
		return clear + 2.1f;
	}

	private void lowerBody(VehicleModel m) {
		float in = inner();
		float z = -hl;
		float step = 1f;
		// merge consecutive slices with the same rounded parameters
		float runZ = z;
		float[] last = null;
		while (z < hl - 1e-3f) {
			float z1 = Math.min(hl, z + step);
			float zc = (z + z1) / 2;
			float yt = top(zc), yb = bot(zc), w = hw(zc), a = arch(zc);
			if (a > 0) a = Math.min(a, yt - 1.2f);
			float[] cur = {r(yt), r(yb), r(w), r(a), inDoor(zc) ? 1 : 0};
			if (last != null && !same(last, cur)) {
				slice(m, runZ, z, last, in);
				runZ = z;
			}
			last = cur;
			z = z1;
		}
		if (last != null) slice(m, runZ, hl, last, in);
	}

	private static float r(float v) { return Math.round(v * 4) / 4f; }

	private static boolean same(float[] a, float[] b) {
		for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
		return true;
	}

	private void slice(VehicleModel m, float z0, float z1, float[] p, float in) {
		float yt = p[0], yb = p[1], w = p[2], a = p[3];
		if (a > yb) {
			// arch: fender over the wheel on each side, black wheel well in the middle below the arch
			column(m, -w, -in, a, yt, z0, z1);
			column(m, in, w, a, yt, z0, z1);
			column(m, -in, in, yb, yt, z0, z1);
			m.box(Mat.BLACK, -in, Math.max(yb, wr(axleR) * 0.5f), z0, -in + 0.01f, a, z1).faces(1);
			m.box(Mat.BLACK, in - 0.01f, Math.max(yb, wr(axleR) * 0.5f), z0, in, a, z1).faces(2);
		} else if (p.length > 4 && p[4] == 1 && yt - yb > 4) {
			// front doors: full-width sill, then a door skin with an interior card on each side over an open cabin
			float sill = Math.max(yb + 0.5f, Math.min(yt - 2, doorFloor()));
			column(m, -w, w, yb, sill, z0, z1);
			for (int side = -1; side <= 1; side += 2) {
				int n0 = m.boxes.size();
				float xo = side * w, xi = side * (w - DOOR_SKIN);
				column(m, Math.min(xo, xi), Math.max(xo, xi), sill, yt, z0, z1);
				float xc = side * (w - DOOR_SKIN - 0.3f);
				m.box(Mat.INTERIOR, Math.min(xi, xc), sill, z0, Math.max(xi, xc), yt - 0.3f, z1);
				for (int i = n0; i < m.boxes.size(); i++) m.boxes.get(i).door(side);
			}
		} else {
			column(m, -w, w, yb, yt, z0, z1);
		}
	}

	/** One vertical run of the lower body, split for the two-tone/livery band. */
	private void column(VehicleModel m, float x0, float x1, float y0, float y1, float z0, float z1) {
		if (y1 <= y0) return;
		if (toneY > 0 && toneY > y0 && toneY < y1) {
			float t2 = toneY2 > 0 ? Math.max(y0, Math.min(toneY2, toneY)) : y0;
			if (t2 > y0) m.box(lower, x0, y0, z0, x1, t2, z1);
			m.box(lower2, x0, t2, z0, x1, toneY, z1);
			m.box(lower, x0, toneY, z0, x1, y1, z1);
		} else {
			m.box(toneY > 0 && y1 <= toneY ? lower2 : lower, x0, y0, z0, x1, y1, z1);
		}
	}

	private void wells(VehicleModel m) {
		float in = inner();
		for (float ax : new float[] {axleF, axleR}) {
			float r = archR(ax);
			// inner well liner and a dark roof over the tyre
			if (ax == axleF || rearWellRoof) m.box(Mat.BLACK, -in, wr(ax) * 2 + 0.2f, ax - r + 0.5f, in, wr(ax) * 2 + 0.8f, ax + r - 0.5f);
			// rubber arch lip
			float w = hw(ax);
			float lipY = Math.min(arch(ax), top(ax) - 1.2f);
			m.box(Mat.RUBBER, -w - 0.05f, lipY - 0.6f, ax - 1.2f, -w + 1.2f, lipY, ax + 1.2f);
			m.box(Mat.RUBBER, w - 1.2f, lipY - 0.6f, ax - 1.2f, w + 0.05f, lipY, ax + 1.2f);
		}
		// floor pan
		m.box(Mat.BLACK, -in + 0.5f, clear - 0.8f, axleR + 2, in - 0.5f, clear + 0.2f, axleF - 2);
	}

	private void greenhouse(VehicleModel m) {
		float roofThick = 1.0f;
		// windshield and pillars: smooth slanted slabs
		float gwA = hw(zA) - gi;
		float gwR = hw(zR1) - gi - ri;
		float bA = top(zA);
		float wsTop = convertible ? Math.min(roofY - 1, bA + (roofY - bA) * 0.7f) : roofY - roofThick;
		float wsZTop = convertible ? lerp(zA, zR1, 0.7f) : zR1;
		VehicleModels.slab(m, glass, -gwA + 0.8f, gwA - 0.8f, zA, bA, wsZTop, wsTop, 0.45f);
		VehicleModels.slab(m, pillar, gwA - 1.3f, gwA + 0.1f, zA, bA, wsZTop, wsTop, 0.9f);
		VehicleModels.slab(m, pillar, -gwA - 0.1f, -gwA + 1.3f, zA, bA, wsZTop, wsTop, 0.9f);
		if (convertible) {
			m.box(pillar, -gwA, wsTop - 0.2f, wsZTop - 0.6f, gwA, wsTop + 0.8f, wsZTop + 0.6f);
			m.box(windowTrim, -hw(0) + 0.2f, top(0) - 0.01f, zC, -hw(0) + 1.4f, top(0) + 0.5f, zA);
			m.box(windowTrim, hw(0) - 1.4f, top(0) - 0.01f, zC, hw(0) - 0.2f, top(0) + 0.5f, zA);
			if (openTop) return;
			// folded hood behind the rear seats
			float fz = zC + 1;
			m.box(Mat.CANVAS, -hw(fz) + 2, top(fz), fz, hw(fz) - 2, top(fz) + 2.6f, fz + 6);
			return;
		}
		float bC = top(zC);
		float gwC = hw(zC) - gi;
		VehicleModels.slab(m, glass, -gwC + 0.8f, gwC - 0.8f, zC, bC, zR2, roofY - roofThick, 0.45f);
		VehicleModels.slab(m, pillar, gwC - 2.2f, gwC + 0.1f, zC, bC, zR2, roofY - roofThick, 0.9f);
		VehicleModels.slab(m, pillar, -gwC - 0.1f, -gwC + 2.2f, zC, bC, zR2, roofY - roofThick, 0.9f);
		// roof panel
		float rw = Math.min(gwR, hw((zR1 + zR2) / 2) - gi - ri);
		for (float z = zR2 - 0.5f; z < zR1 + 0.5f; z += 2) {
			float z1 = Math.min(zR1 + 0.5f, z + 2);
			float y = roofAt((z + z1) / 2);
			m.box(roofMat, -rw - 0.3f, y - roofThick, z, rw + 0.3f, y, z1);
		}
		// side glass and pillar panels, sliced along z so the top edge follows the roof line
		for (int side = -1; side <= 1; side += 2) {
			float runZ = zC, lastY = -1;
			Mat lastMat = null;
			for (float z = zC; z < zA - 1e-3f; z += 1) {
				float z1 = Math.min(zA, z + 1);
				float zc = (z + z1) / 2;
				float b = top(zc);
				float y = r(roofAt(zc) - (zc > zR2 && zc < zR1 ? roofThick : 0.2f));
				Mat mat;
				if (zc > sideFront || zc < sideRear) mat = upper;
				else if (!Float.isNaN(cSplit) && zc < cSplit) mat = rearSideGlass;
				else mat = glass;
				if (lastMat != null && (mat != lastMat || y != lastY || inDoor(zc) != inDoor(zc - 1))) {
					sidePanel(m, side, runZ, z, lastY, lastMat);
					runZ = z;
				}
				lastMat = mat;
				lastY = y;
			}
			if (lastMat != null) sidePanel(m, side, runZ, zA, lastY, lastMat);
			float x = side * (hw(seatZ) - gi - ri * 0.5f);
			if (!Float.isNaN(bPillar)) m.box(pillar, x - 0.5f, top(bPillar), bPillar - bPillarW / 2, x + 0.5f, roofAt(bPillar), bPillar + bPillarW / 2);
			if (!Float.isNaN(cSplit)) m.box(pillar, x - 0.5f, top(cSplit), cSplit - 0.5f, x + 0.5f, roofAt(cSplit), cSplit + 0.5f);
			// belt trim along the base of the windows (the part on the front door swings with it)
			float ty = top((zC + zA) / 2);
			if (!Float.isNaN(doorZ0) && doorZ0 > sideRear && doorZ1 < sideFront) {
				m.box(windowTrim, x - 0.55f, ty - 0.1f, sideRear, x + 0.55f, ty + 0.45f, doorZ0);
				m.box(windowTrim, x - 0.55f, ty - 0.1f, doorZ0, x + 0.55f, ty + 0.45f, doorZ1).door(side);
				m.box(windowTrim, x - 0.55f, ty - 0.1f, doorZ1, x + 0.55f, ty + 0.45f, sideFront);
			} else {
				m.box(windowTrim, x - 0.55f, ty - 0.1f, sideRear, x + 0.55f, ty + 0.45f, sideFront);
			}
		}
	}

	private void sidePanel(VehicleModel m, int side, float z0, float z1, float yTop, Mat mat) {
		float zc = (z0 + z1) / 2;
		float b = top(zc) - 0.05f;
		if (yTop <= b) return;
		float x = side * (hw(zc) - gi - ri * 0.5f);
		boolean isGlass = mat.pass == Mat.Pass.GLASS;
		VehicleModel.Box box = m.box(mat, x - (isGlass ? 0.25f : 0.5f), b, z0, x + (isGlass ? 0.25f : 0.5f), yTop, z1);
		if (inDoor(zc)) box.door(side);
		if (isGlass) {
			box.faces(1 | 2 | 8);
			if (side < 0 && zc > (Float.isNaN(bPillar) ? zR2 : bPillar)) box.group(VehicleModel.G_DRIVER_WINDOW);
		}
	}

	/** Dashboard, steering wheel, seats, console and rear bench. */
	private void interior(VehicleModel m) {
		float b = top(zA);
		float iw = hw(zA) - gi - 1;
		float floor = clear + 1.5f;
		// floor carpet and door cards
		m.box(Mat.INTERIOR, -iw, floor, zC, iw, floor + 0.6f, zA).group(VehicleModel.G_INTERIOR);
		// dash with gauges facing the driver
		m.box(Mat.INTERIOR, -iw, b - 3, zA - 4.5f, iw, b + 0.8f, zA + 1).group(VehicleModel.G_INTERIOR);
		m.box(Mat.DASH, -seatX - 2.5f, b - 2.2f, zA - 4.6f, -seatX + 2.5f, b + 0.4f, zA - 4.5f).group(VehicleModel.G_INTERIOR);
		m.box(Mat.TRIM, -1.6f, floor, seatZ - 6, 1.6f, b - 2, zA - 4).group(VehicleModel.G_INTERIOR);
		// steering wheel: rim as four bars around the column
		float wz = zA - 6.5f, wy = b + 0.5f, wx = -seatX;
		m.box(Mat.BLACK, wx - 0.4f, wy - 2.2f, wz + 1.2f, wx + 0.4f, wy - 0.6f, zA - 4.5f).pitch(-30).group(VehicleModel.G_INTERIOR);
		m.box(Mat.BLACK, wx - 2.2f, wy + 1.4f, wz - 0.4f, wx + 2.2f, wy + 2.0f, wz + 0.4f).group(VehicleModel.G_INTERIOR);
		m.box(Mat.BLACK, wx - 2.2f, wy - 2.0f, wz + 0.6f, wx + 2.2f, wy - 1.4f, wz + 1.4f).group(VehicleModel.G_INTERIOR);
		m.box(Mat.BLACK, wx - 2.4f, wy - 1.6f, wz, wx - 1.8f, wy + 1.6f, wz + 1.2f).group(VehicleModel.G_INTERIOR);
		m.box(Mat.BLACK, wx + 1.8f, wy - 1.6f, wz, wx + 2.4f, wy + 1.6f, wz + 1.2f).group(VehicleModel.G_INTERIOR);
		// front seats (cushion top sits at the rider's hip)
		float hip = seatY + 2.2f;
		for (int side = -1; side <= 1; side += 2) seat(m, side * seatX, Math.max(floor + 1, hip - 0.4f), seatZ, Math.min(roofY - 2.5f, hip + 10));
		if (!Float.isNaN(rearSeatZ)) {
			float riw = Math.min(iw, hw(rearSeatZ) - gi - 1.2f);
			float rh = Math.max(floor + 1, hip - 0.4f);
			m.box(seatMat, -riw, floor, rearSeatZ - 4, riw, rh, rearSeatZ + 3).group(VehicleModel.G_INTERIOR);
			m.box(seatMat, -riw, rh, rearSeatZ - 5.5f, riw, Math.min(roofY - 3f, rh + 9), rearSeatZ - 3.5f).pitch(8).group(VehicleModel.G_INTERIOR);
		}
	}

	Mat seatMat = Mat.LEATHER;

	private void seat(VehicleModel m, float x, float cushion, float z, float backTop) {
		float floor = clear + 1.5f;
		m.box(seatMat, x - 3, floor, z - 3, x + 3, cushion, z + 3.5f).group(VehicleModel.G_INTERIOR);
		m.box(seatMat, x - 3, cushion, z - 4.6f, x + 3, backTop, z - 2.8f).pitch(10).group(VehicleModel.G_INTERIOR);
		m.box(seatMat, x - 1.8f, backTop, z - 5.6f, x + 1.8f, backTop + 2.2f, z - 4.2f).group(VehicleModel.G_INTERIOR);
		m.box(Mat.TRIM, x - 3.1f, cushion - 0.6f, z - 3.1f, x - 2.6f, cushion + 1.6f, z + 3.6f).group(VehicleModel.G_INTERIOR);
	}
}
