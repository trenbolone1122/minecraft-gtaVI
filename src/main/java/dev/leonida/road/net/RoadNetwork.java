package dev.leonida.road.net;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The Leonida road network in block space (x = map x, z = -map y), loaded from the baked
 * {@code data/leonida/road/roads.bin.gz} (our pipeline's own road profiles and highway vertical
 * engineering over community-world-v1, see tools/roadbake/bake.mjs). Immutable and thread safe after
 * {@link #get()}; every query is a pure function of the data, so generation is deterministic.
 */
public final class RoadNetwork {
	public static final double SIDEWALK = 3.0;   // kerb block + 2 sidewalk blocks beyond the carriageway
	public static final double SHOULDER = 1.0;   // highway barrier block beyond the carriageway
	public static final double VERGE = 7.0;      // terrain graded towards the road over this width
	public static final double BLEND = 14.0;     // arm length over which the height eases into the flat junction pad
	static final int CELL = 32;

	private static volatile RoadNetwork instance;

	public final List<Node> nodes;
	public final List<Segment> segments;
	private final int minCx, minCz, cw, ch;
	private final int[][] cells; // piece refs: segment index << 12 | piece index

	/** The network if loaded, else null (never blocks). */
	public static RoadNetwork peek() {
		return instance;
	}

	public static RoadNetwork get() {
		RoadNetwork n = instance;
		if (n == null) {
			synchronized (RoadNetwork.class) {
				if (instance == null) instance = load();
				n = instance;
			}
		}
		return n;
	}

	private static RoadNetwork load() {
		try (InputStream raw = RoadNetwork.class.getResourceAsStream("/data/leonida/road/roads.bin.gz")) {
			if (raw == null) throw new IllegalStateException("roads.bin.gz missing");
			return new RoadNetwork(new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16)));
		} catch (IOException e) {
			throw new IllegalStateException("cannot read road network", e);
		}
	}

	private RoadNetwork(DataInputStream in) throws IOException {
		if (in.readInt() != 0x4c524431) throw new IOException("bad magic");
		in.readInt();
		int nn = in.readInt();
		List<Node> ns = new ArrayList<>(nn);
		for (int i = 0; i < nn; i++) {
			double x = in.readFloat(), y = in.readFloat(), z = in.readFloat();
			ns.add(new Node(i, x, -y, z));
		}
		int sn = in.readInt();
		List<Segment> ss = new ArrayList<>(sn);
		for (int i = 0; i < sn; i++) {
			int kind = in.readUnsignedByte(); // 0 street, 1 highway, 2 street with kerb parking both sides
			boolean hw = kind == 1;
			double w = in.readFloat();
			int a = in.readInt(), b = in.readInt(), n = in.readInt();
			double[] x = new double[n], z = new double[n], h = new double[n], g = new double[n];
			byte[] f = new byte[n];
			for (int k = 0; k < n; k++) {
				x[k] = in.readFloat();
				z[k] = -in.readFloat();
				h[k] = in.readFloat();
				g[k] = in.readFloat();
				f[k] = in.readByte();
			}
			Segment s = new Segment(i, hw, kind == 2, w, a < 0 ? null : ns.get(a), b < 0 ? null : ns.get(b), x, z, h, g, f);
			ss.add(s);
			if (s.a != null) s.a.arms.add(new Arm(s, true));
			if (s.b != null) s.b.arms.add(new Arm(s, false));
		}
		this.nodes = List.copyOf(ns);
		this.segments = List.copyOf(ss);
		for (Node node : nodes) node.finish();
		for (Segment s : segments) s.finish();

		// spatial index of pieces (sample intervals) by reach box
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Segment s : segments) for (int k = 0; k < s.n; k++) {
			minX = Math.min(minX, s.x[k]); maxX = Math.max(maxX, s.x[k]);
			minZ = Math.min(minZ, s.z[k]); maxZ = Math.max(maxZ, s.z[k]);
		}
		int pad = 40;
		minCx = Math.floorDiv((int) Math.floor(minX) - pad, CELL);
		minCz = Math.floorDiv((int) Math.floor(minZ) - pad, CELL);
		cw = Math.floorDiv((int) Math.ceil(maxX) + pad, CELL) - minCx + 1;
		ch = Math.floorDiv((int) Math.ceil(maxZ) + pad, CELL) - minCz + 1;
		int[] counts = new int[cw * ch];
		int[][] tmp = null;
		for (int pass = 0; pass < 2; pass++) {
			if (pass == 1) tmp = new int[cw * ch][];
			if (pass == 1) for (int c = 0; c < counts.length; c++) { tmp[c] = new int[counts[c]]; counts[c] = 0; }
			for (Segment s : segments) {
				double r = s.reach();
				for (int k = 0; k + 1 < s.n; k++) {
					int x0 = Math.floorDiv((int) Math.floor(Math.min(s.x[k], s.x[k + 1]) - r), CELL) - minCx;
					int x1 = Math.floorDiv((int) Math.ceil(Math.max(s.x[k], s.x[k + 1]) + r), CELL) - minCx;
					int z0 = Math.floorDiv((int) Math.floor(Math.min(s.z[k], s.z[k + 1]) - r), CELL) - minCz;
					int z1 = Math.floorDiv((int) Math.ceil(Math.max(s.z[k], s.z[k + 1]) + r), CELL) - minCz;
					for (int cz = z0; cz <= z1; cz++) for (int cx = x0; cx <= x1; cx++) {
						int c = cz * cw + cx;
						if (pass == 1) tmp[c][counts[c]] = s.id << 12 | k;
						counts[c]++;
					}
				}
			}
		}
		this.cells = tmp;
	}

	/** Piece refs whose reach box touches the 32 block cell containing (x, z). */
	int[] piecesAt(int x, int z) {
		int cx = Math.floorDiv(x, CELL) - minCx, cz = Math.floorDiv(z, CELL) - minCz;
		if (cx < 0 || cz < 0 || cx >= cw || cz >= ch) return EMPTY;
		int[] c = cells[cz * cw + cx];
		return c == null ? EMPTY : c;
	}

	private static final int[] EMPTY = new int[0];

	/**
	 * Every road element covering block column (x, z), measured at the column centre. The list holds
	 * one hit per nearby segment (its closest piece), unsorted.
	 */
	public List<Hit> hitsAt(int x, int z) {
		List<Hit> out = new ArrayList<>(4);
		int[] refs = piecesAt(x, z);
		double px = x + 0.5, pz = z + 0.5;
		for (int ref : refs) {
			Segment s = segments.get(ref >>> 12);
			int k = ref & 0xfff;
			Hit h = s.measure(k, px, pz);
			if (h == null) continue;
			// keep only the closest piece per segment
			boolean replaced = false;
			for (int i = 0; i < out.size(); i++) {
				Hit o = out.get(i);
				if (o.seg == s) {
					if (h.dist < o.dist - 1e-9) out.set(i, h);
					replaced = true;
					break;
				}
			}
			if (!replaced) out.add(h);
		}
		return out;
	}

	/** Nodes within r blocks of (x, z). */
	public List<Node> nodesNear(double x, double z, double r) {
		List<Node> out = new ArrayList<>();
		int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r), z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r);
		java.util.HashSet<Node> seen = new java.util.HashSet<>();
		for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z1, CELL); cz++)
			for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x1, CELL); cx++)
				for (int ref : piecesAt(cx * CELL, cz * CELL)) {
					Segment s = segments.get(ref >>> 12);
					for (Node n : new Node[] {s.a, s.b})
						if (n != null && seen.add(n) && Math.hypot(n.x - x, n.z - z) <= r) out.add(n);
				}
		return out;
	}

	/** Segments with a piece within r blocks of (x, z). */
	public List<Segment> segmentsNear(double x, double z, double r) {
		java.util.LinkedHashSet<Segment> out = new java.util.LinkedHashSet<>();
		int x0 = (int) Math.floor(x - r), x1 = (int) Math.floor(x + r), z0 = (int) Math.floor(z - r), z1 = (int) Math.floor(z + r);
		for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z1, CELL); cz++)
			for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x1, CELL); cx++)
				for (int ref : piecesAt(cx * CELL, cz * CELL)) {
					Segment s = segments.get(ref >>> 12);
					if (out.contains(s)) continue;
					int k = ref & 0xfff;
					Hit h = s.measure(k, x, z, r + s.width / 2);
					if (h != null) out.add(s);
				}
		return new ArrayList<>(out);
	}

	/** Junction or dead end shared by road segments. */
	public static final class Node {
		public final int id;
		public final double x, z, h;
		public final List<Arm> arms = new ArrayList<>(4);
		/** Main axis angle (radians, block space) used for signal phases: arms within 45 degrees of it share phase A. */
		public double axis;
		public boolean signalled;
		public boolean highway;

		Node(int id, double x, double z, double h) {
			this.id = id;
			this.x = x;
			this.z = z;
			this.h = h;
		}

		public boolean isJunction() {
			return arms.size() >= 3;
		}

		void finish() {
			for (Arm a : arms) a.angle = a.seg.leaveAngle(a.atStart);
			boolean anyStreet = false, anyHighway = false;
			for (Arm a : arms) {
				if (a.seg.highway) anyHighway = true;
				else anyStreet = true;
			}
			highway = anyHighway;
			int wide = 0;
			for (Arm a : arms) if (!a.seg.highway && a.seg.width >= 12) wide++;
			signalled = arms.size() >= 3 && anyStreet && !anyHighway && wide >= 2;
			// main axis = the widest arm
			Arm best = null;
			for (Arm a : arms) if (best == null || a.seg.width > best.seg.width) best = a;
			axis = best == null ? 0 : best.angle;
			// mouth station of every arm: where its carriageway leaves the other arms' strips (plus corner room)
			for (Arm a : arms) {
				double hi = a.seg.width / 2, m = 0;
				if (arms.size() >= 3) {
					for (Arm b : arms) {
						if (b == a) continue;
						double hj = b.seg.width / 2 + (b.seg.highway ? SHOULDER : SIDEWALK);
						double th = angleBetween(a.angle, b.angle);
						th = Math.max(Math.toRadians(25), th);
						double d = th > Math.PI / 2 + 1e-6 && th > Math.toRadians(155) ? 0 : (hj + hi * Math.abs(Math.cos(th))) / Math.sin(th);
						if (th >= Math.toRadians(155)) d = 0; // straight through: no conflict
						m = Math.max(m, d);
					}
					m += 1.5;
				}
				a.mouth = Math.min(m, a.seg.length * 0.45);
			}
		}

		/** Signal phase for traffic arriving on an arm whose leave angle is {@code armAngle}. */
		public boolean onMainAxis(double armAngle) {
			double d = Math.abs(Math.IEEEremainder(armAngle - axis, Math.PI));
			return d < Math.PI / 4;
		}
	}

	static double angleBetween(double a, double b) {
		double d = Math.abs(Math.IEEEremainder(a - b, 2 * Math.PI));
		return d;
	}

	/** One segment end at a node. */
	public static final class Arm {
		public final Segment seg;
		public final boolean atStart;
		/** Direction leaving the node along the segment (radians, atan2(dz, dx)). */
		public double angle;
		/** Distance from the node along the arm where its own markings may begin (junction pad edge). */
		public double mouth;

		Arm(Segment seg, boolean atStart) {
			this.seg = seg;
			this.atStart = atStart;
		}
	}

	/** Column measurement against one road segment. */
	public static final class Hit {
		public Segment seg;
		public int piece;
		/** Signed lateral offset in blocks, positive = right of travel in +s direction. */
		public double u;
		/** Distance to the centre line (|u| for interior points, radial at piece ends). */
		public double dist;
		/** Station along the segment. */
		public double s;
		/** Piece direction (radians, atan2(dz, dx)). */
		public double angle;
		/** Road surface height in metres above sea, junction-flattened. */
		public double h;
		/** Terrain height under the road (profile ground), metres. */
		public double ground;
		/** Inside a junction pad (no lane markings). */
		public boolean pad;
		public boolean bridge, water;
	}
}
