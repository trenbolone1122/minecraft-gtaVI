package dev.leonida.road.net;

import dev.leonida.road.net.RoadNetwork.Hit;
import dev.leonida.road.net.RoadNetwork.Node;

/**
 * One road polyline from the baked network: samples every ~6 m with the engineered surface height
 * (metres above sea), profile ground and bridge / water flags. Lane layout follows the leonidastate3d
 * look rules (1.2 m shoulders with solid white edges, double yellow centre on two-way streets, dashed
 * white lanes); highways are divided by a jersey barrier with yellow inner edge lines.
 */
public final class Segment {
	public static final double SHOULDER_INSET = 1.2;
	public static final double MEDIAN_LINE = 1.2;
	/** Half width of the raised planted median on wide boulevards (0 = none). */
	public static final double PLANTER_HALF = 1.8;
	/** Planted medians stop this far short of the junction mouth (left-turn pocket and crossing). */
	public static final double PLANTER_NOSE = 7.0;
	/** Kerb parking strip width (kind 2 streets): between the solid edge line and the kerb. */
	public static final double PARKING = 2.4;
	/** Parking bay length along the kerb. */
	public static final double BAY = 6.0;

	public final int id;
	public final boolean highway;
	/** Kerb parking strip on both sides, outside the edge lines (authored Ocean Drive). */
	public final boolean parking;
	public final double width;
	public final Node a, b;
	public final int n;
	final double[] x, z, h, g, s;
	final byte[] f;
	public double length;

	/** Lateral offset of the solid white edge lines (both sides). */
	public double edge;
	/** Planted median half width (boulevards 20 m wide), 0 if none. */
	public final double median;
	/** Lanes per direction. */
	public int lanesPerSide;
	/** Lane width in blocks. */
	public double laneWidth;
	/** Lateral offset where the lanes of one direction start (0 for streets, median line for highways). */
	public double inner;
	RoadNetwork.Arm armA, armB;

	Segment(int id, boolean highway, boolean parking, double width, Node a, Node b, double[] x, double[] z, double[] h, double[] g, byte[] f) {
		if (x.length > 4095) throw new IllegalStateException("segment " + id + " has too many samples");
		this.id = id;
		this.highway = highway;
		this.parking = parking && !highway;
		this.width = width;
		this.a = a;
		this.b = b;
		this.n = x.length;
		this.x = x;
		this.z = z;
		this.h = h;
		this.g = g;
		this.f = f;
		this.s = new double[n];
		for (int k = 1; k < n; k++) s[k] = s[k - 1] + Math.hypot(x[k] - x[k - 1], z[k] - z[k - 1]);
		length = s[n - 1];
		double half = width / 2;
		edge = this.parking ? half - PARKING : Math.max(half * 0.6, half - SHOULDER_INSET);
		median = !highway && width >= 19.5 ? PLANTER_HALF : 0;
		inner = highway ? MEDIAN_LINE : median > 0 ? median + 0.6 : 0;
		double usable = edge - inner;
		double minLane = highway ? 3.3 : 2.9;
		lanesPerSide = Math.max(1, Math.min(4, (int) Math.floor(usable / minLane)));
		laneWidth = usable / lanesPerSide;
	}

	void finish() {
		if (a != null) for (RoadNetwork.Arm arm : a.arms) if (arm.seg == this && arm.atStart) armA = arm;
		if (b != null) for (RoadNetwork.Arm arm : b.arms) if (arm.seg == this && !arm.atStart) armB = arm;
	}

	public double half() {
		return width / 2;
	}

	/** Footprint half width: carriageway plus sidewalks (streets) or barrier shoulder (highways). */
	public double footprint() {
		return width / 2 + (highway ? RoadNetwork.SHOULDER : RoadNetwork.SIDEWALK);
	}

	double reach() {
		return footprint() + RoadNetwork.VERGE + 1;
	}

	public double mouthStart() {
		return armA == null ? 0 : armA.mouth;
	}

	public double mouthEnd() {
		return armB == null ? 0 : armB.mouth;
	}

	/** Distance from station st to the nearer junction mouth (large when neither end is a junction). */
	public double junctionDistance(double st) {
		double e0 = junctionAtStart() ? st - mouthStart() : 1e9;
		double e1 = junctionAtEnd() ? (length - st) - mouthEnd() : 1e9;
		return Math.min(e0, e1);
	}

	/** True where the planted median is built at station st. */
	public boolean planterAt(double st) {
		return median > 0 && junctionDistance(st) > PLANTER_NOSE;
	}

	public boolean junctionAtStart() {
		return a != null && a.isJunction();
	}

	public boolean junctionAtEnd() {
		return b != null && b.isJunction();
	}

	double leaveAngle(boolean atStart) {
		if (atStart) {
			int k = 1;
			while (k < n - 1 && s[k] < 8) k++;
			return Math.atan2(z[k] - z[0], x[k] - x[0]);
		}
		int k = n - 2;
		while (k > 0 && length - s[k] < 8) k--;
		return Math.atan2(z[k] - z[n - 1], x[k] - x[n - 1]);
	}

	Hit measure(int k, double px, double pz) {
		return measure(k, px, pz, reach());
	}

	Hit measure(int k, double px, double pz, double reach) {
		double x0 = x[k], z0 = z[k], dx = x[k + 1] - x0, dz = z[k + 1] - z0;
		double len2 = dx * dx + dz * dz;
		if (len2 < 1e-9) return null;
		double len = Math.sqrt(len2);
		double t = ((px - x0) * dx + (pz - z0) * dz) / len2;
		double tc = Math.max(0, Math.min(1, t));
		double cx = x0 + dx * tc, cz = z0 + dz * tc;
		double dist = Math.hypot(px - cx, pz - cz);
		if (dist > reach) return null;
		double side = ((px - x0) * -dz + (pz - z0) * dx) / len;
		// square off free (dead) ends instead of a round cap
		if ((k == 0 && t < 0 && (a == null || a.arms.size() == 1) && -t * len > 0.5)
				|| (k == n - 2 && t > 1 && (b == null || b.arms.size() == 1) && (t - 1) * len > 0.5)) return null;
		Hit hit = new Hit();
		hit.seg = this;
		hit.piece = k;
		hit.dist = dist;
		hit.u = (t >= 0 && t <= 1) ? side : Math.copySign(dist, side);
		hit.s = s[k] + tc * len;
		hit.angle = Math.atan2(dz, dx);
		double hh = h[k] + tc * (h[k + 1] - h[k]);
		hit.ground = g[k] + tc * (g[k + 1] - g[k]);
		hit.bridge = tc < 0.5 ? (f[k] & 1) != 0 : (f[k + 1] & 1) != 0;
		hit.water = ((f[k] | f[k + 1]) & 2) != 0;
		// flat junction pads, eased into the profile along each arm
		if (junctionAtStart()) {
			double m = mouthStart();
			if (hit.s < m) { hh = a.h; hit.pad = true; }
			else if (hit.s < m + RoadNetwork.BLEND) hh = lerp(a.h, hh, smooth((hit.s - m) / RoadNetwork.BLEND));
		}
		if (junctionAtEnd()) {
			double m = mouthEnd(), r = length - hit.s;
			if (r < m) { hh = b.h; hit.pad = true; }
			else if (r < m + RoadNetwork.BLEND) hh = lerp(b.h, hh, smooth((r - m) / RoadNetwork.BLEND));
		}
		hit.h = hh;
		return hit;
	}

	/** Point on the centre line at station st: {x, z, h, angle}. */
	public double[] at(double st) {
		st = Math.max(0, Math.min(length, st));
		int k = 0;
		int lo = 0, hi = n - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (s[mid] <= st) lo = mid;
			else hi = mid;
		}
		k = Math.min(lo, n - 2);
		double len = s[k + 1] - s[k], t = len < 1e-9 ? 0 : (st - s[k]) / len;
		double hh = h[k] + t * (h[k + 1] - h[k]);
		if (junctionAtStart() && st < mouthStart() + RoadNetwork.BLEND)
			hh = st < mouthStart() ? a.h : lerp(a.h, hh, smooth((st - mouthStart()) / RoadNetwork.BLEND));
		if (junctionAtEnd() && length - st < mouthEnd() + RoadNetwork.BLEND)
			hh = length - st < mouthEnd() ? b.h : lerp(b.h, hh, smooth((length - st - mouthEnd()) / RoadNetwork.BLEND));
		return new double[] {x[k] + t * (x[k + 1] - x[k]), z[k] + t * (z[k + 1] - z[k]), hh, Math.atan2(z[k + 1] - z[k], x[k + 1] - x[k]),
				g[k] + t * (g[k + 1] - g[k]), (f[t < .5 ? k : k + 1] & 1)};
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double smooth(double t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}
}
