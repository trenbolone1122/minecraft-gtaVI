package dev.leonida.road.net;

import dev.leonida.road.net.RoadNetwork.Arm;
import dev.leonida.road.net.RoadNetwork.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Street furniture layout, a pure function of the network so the server (pole blocks, lamp light)
 * and the client (arms, lamp heads, signal heads, wires at exact angles) agree without syncing:
 * streetlights every 32 m on alternating sides (paired on the highway median), Florida mast-arm
 * signals with two heads per approach at signalled junctions, wooden power poles with sagging lines
 * along suburban streets.
 */
public final class Props {
	public static final double LAMP_SPACING = 32, HIGHWAY_LAMP_SPACING = 48, POWER_SPACING = 40;
	public static final int LAMP_HEIGHT = 9, HIGHWAY_LAMP_HEIGHT = 11, SIGNAL_HEIGHT = 7, POWER_HEIGHT = 10;
	static final int CELL = 32;
	private static final double SEA = 62;

	public enum Kind { LAMP, MEDIAN_LAMP, SIGNAL, POWER }

	/** One pole. x, z: pole axis (block space); baseY: first pole block; arm: direction the arm reaches (radians). */
	public static final class Pole {
		public Kind kind;
		public double x, z;
		public int baseY, height;
		public double armAngle, armLen;
		/** Signal: arm distances of the two heads, the direction the lights face, the node and its axis. */
		public double[] heads;
		public double faceAngle;
		public int node = -1;
		public boolean mainAxis;
		/** Power: the next pole along the line (wires run to it), or null. */
		public Pole next;
		public boolean lamp; // power pole carries a cobra-head streetlight too
		public int segment;

		public int bx() {
			return (int) Math.floor(x);
		}

		public int bz() {
			return (int) Math.floor(z);
		}

		/** Head end of the arm (block space x, z) and the Y of the arm. */
		public double armX() {
			return x + Math.cos(armAngle) * armLen;
		}

		public double armZ() {
			return z + Math.sin(armAngle) * armLen;
		}

		public double topY() {
			return baseY + height;
		}
	}

	private static volatile Props instance;
	public final List<Pole> poles = new ArrayList<>();
	private final java.util.HashMap<Long, List<Pole>> cells = new java.util.HashMap<>();

	/** The layout if it has been built already, else null (never blocks; for the renderer). */
	public static Props peek() {
		return instance;
	}

	public static Props get() {
		Props p = instance;
		if (p == null) {
			synchronized (Props.class) {
				if (instance == null) instance = new Props(RoadNetwork.get());
				p = instance;
			}
		}
		return p;
	}

	/** Poles whose axis lies in the box [x0, x1) x [z0, z1). */
	public List<Pole> in(int x0, int z0, int x1, int z1) {
		List<Pole> out = new ArrayList<>();
		for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z1 - 1, CELL); cz++)
			for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x1 - 1, CELL); cx++) {
				List<Pole> l = cells.get(key(cx, cz));
				if (l == null) continue;
				for (Pole p : l) if (p.x >= x0 && p.x < x1 && p.z >= z0 && p.z < z1) out.add(p);
			}
		return out;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xffffffffL);
	}

	private void add(Pole p) {
		poles.add(p);
		cells.computeIfAbsent(key(Math.floorDiv(p.bx(), CELL), Math.floorDiv(p.bz(), CELL)), k -> new ArrayList<>()).add(p);
	}

	static double hash01(int a, int b) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Top face height (block space Y) of the road surface for a height in metres. */
	static double surfaceTop(double h) {
		return SEA + 1 + Math.round(h * 2) / 2.0;
	}

	/** True when this street gets wooden power poles (narrow suburban streets). */
	public static boolean suburban(Segment s) {
		return !s.highway && s.width <= 13.5;
	}


	private Props(RoadNetwork net) {
		for (Segment s : net.segments) {
			if (s.highway) medianLamps(s);
			else if (suburban(s)) powerLine(s);
			else streetLamps(s);
		}
		for (Node n : net.nodes) if (n.signalled) signals(n);
	}

	private static double startClear(Segment s) {
		return s.junctionAtStart() ? s.mouthStart() + 8 : 4;
	}

	private static double endClear(Segment s) {
		return s.junctionAtEnd() ? s.mouthEnd() + 8 : 4;
	}

	private void streetLamps(Segment s) {
		double first = startClear(s), last = s.length - endClear(s);
		if (last - first < 8) return;
		double phase = hash01(s.id, 11) * LAMP_SPACING;
		int k = 0;
		for (double st = first + phase % (LAMP_SPACING / 2); st <= last; st += LAMP_SPACING / 2, k++) {
			int side = ((k + s.id) & 1) == 0 ? 1 : -1;
			double[] c = s.at(st);
			double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
			double off = s.half() + 1.6;
			Pole p = new Pole();
			p.kind = Kind.LAMP;
			p.segment = s.id;
			p.x = c[0] + nx * side * off;
			p.z = c[1] + nz * side * off;
			p.baseY = (int) Math.floor(surfaceTop(c[2]) + 0.5); // on the raised sidewalk
			p.height = LAMP_HEIGHT;
			p.armAngle = Math.atan2(-nz * side, -nx * side);
			p.armLen = 2.4;
			add(p);
		}
	}

	private void medianLamps(Segment s) {
		double first = startClear(s), last = s.length - endClear(s);
		if (last - first < 8) return;
		double phase = hash01(s.id, 13) * HIGHWAY_LAMP_SPACING;
		for (double st = first + phase; st <= last; st += HIGHWAY_LAMP_SPACING) {
			double[] c = s.at(st);
			double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
			Pole p = new Pole();
			p.kind = Kind.MEDIAN_LAMP;
			p.segment = s.id;
			p.x = c[0];
			p.z = c[1];
			p.baseY = (int) Math.floor(surfaceTop(c[2])) + 1; // on the barrier base
			if (Math.round(c[2] * 2) % 2 != 0) p.baseY = (int) Math.floor(surfaceTop(c[2]) + 0.5) + 1;
			p.height = HIGHWAY_LAMP_HEIGHT;
			p.armAngle = Math.atan2(nz, nx);
			p.armLen = 2.8;
			add(p);
		}
	}

	private void powerLine(Segment s) {
		double first = Math.min(6, s.length / 2), last = s.length - Math.min(6, s.length / 2);
		if (last - first < 10) return;
		int side = (s.id & 1) == 0 ? 1 : -1;
		int n = Math.max(1, (int) Math.round((last - first) / POWER_SPACING));
		double step = (last - first) / n;
		Pole prev = null;
		for (int i = 0; i <= n; i++) {
			double st = first + i * step;
			double[] c = s.at(st);
			if (c[5] != 0) { prev = null; continue; } // no poles on bridges
			double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
			double off = s.half() + RoadNetwork.SIDEWALK + 0.6;
			Pole p = new Pole();
			p.kind = Kind.POWER;
			p.segment = s.id;
			p.x = c[0] + nx * side * off;
			p.z = c[1] + nz * side * off;
			p.baseY = (int) Math.floor(surfaceTop(c[2]) + 0.5);
			p.height = POWER_HEIGHT;
			p.armAngle = Math.atan2(-nz * side, -nx * side);
			p.armLen = 1.8;
			p.lamp = (i & 1) == 0 && st > startClear(s) - 4 && st < s.length - endClear(s) + 4;
			if (prev != null) prev.next = p;
			prev = p;
			add(p);
		}
	}

	private void signals(Node n) {
		for (Arm a : n.arms) {
			Segment s = a.seg;
			double st = a.atStart ? a.mouth + 0.5 : s.length - a.mouth - 0.5;
			if (st <= 0 || st >= s.length) continue;
			double[] c = s.at(st);
			double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
			// approaching traffic drives on the right of its travel direction: u < 0 at the start node, u > 0 at the end node
			int side = a.atStart ? -1 : 1;
			double off = s.half() + 1.6;
			Pole p = new Pole();
			p.kind = Kind.SIGNAL;
			p.segment = s.id;
			p.node = n.id;
			p.mainAxis = n.onMainAxis(a.angle);
			p.x = c[0] + nx * side * off;
			p.z = c[1] + nz * side * off;
			p.baseY = (int) Math.floor(surfaceTop(c[2]) + 0.5);
			p.height = SIGNAL_HEIGHT;
			p.armAngle = Math.atan2(-nz * side, -nx * side);
			p.armLen = off - 0.6; // reaches the centre line
			double h0 = Math.max(1.2, off - (s.edge - s.laneWidth * 0.5)), h1 = off - (s.inner + s.laneWidth * 0.5);
			if (h1 - h0 < 2.5) h1 = Math.min(p.armLen, h0 + 2.5);
			p.heads = new double[] {h0, h1};
			// lights face the approaching traffic: towards +station at the start node, -station at the end node
			p.faceAngle = a.atStart ? c[3] : c[3] + Math.PI;
			add(p);
		}
	}
}
