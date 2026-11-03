package dev.leonida.world.data;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/** Spatial index and A* router over the roads.json centreline graph (junction ids as nodes). */
public final class RoadNet {
	private static final int CELL = 32;
	private static volatile RoadNet instance;

	public static RoadNet get() {
		RoadNet r = instance;
		if (r == null) {
			synchronized (RoadNet.class) {
				if (instance == null) instance = new RoadNet(CwFeatures.get());
				r = instance;
			}
		}
		return r;
	}

	private final CwFeatures f;
	/** cell -> packed (segment << 12 | pieceIndex) */
	private final Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>();
	private final double[] segLen;
	private final List<IntArrayList> adj = new ArrayList<>();

	public record Hit(int seg, int piece, double dist, double t, double px, double pz) {}

	private static long key(int a, int b) { return ((long) a << 32) ^ (b & 0xFFFFFFFFL); }

	private RoadNet(CwFeatures f) {
		this.f = f;
		Long2ObjectOpenHashMap<IntArrayList> tmp = new Long2ObjectOpenHashMap<>();
		segLen = new double[f.roads.size()];
		int maxNode = f.junctions.size();
		for (CwFeatures.Road r : f.roads) maxNode = Math.max(maxNode, Math.max(r.a(), r.b()) + 1);
		for (int i = 0; i < maxNode; i++) adj.add(new IntArrayList());
		for (int s = 0; s < f.roads.size(); s++) {
			CwFeatures.Road r = f.roads.get(s);
			float[] p = r.xz();
			double pad = r.width() / 2 + 4;
			for (int i = 0; i + 3 < p.length; i += 2) {
				double ax = p[i], az = p[i + 1], bx = p[i + 2], bz = p[i + 3];
				segLen[s] += Math.hypot(bx - ax, bz - az);
				int cx0 = (int) Math.floor((Math.min(ax, bx) - pad) / CELL), cx1 = (int) Math.floor((Math.max(ax, bx) + pad) / CELL);
				int cz0 = (int) Math.floor((Math.min(az, bz) - pad) / CELL), cz1 = (int) Math.floor((Math.max(az, bz) + pad) / CELL);
				for (int cx = cx0; cx <= cx1; cx++)
					for (int cz = cz0; cz <= cz1; cz++) tmp.computeIfAbsent(key(cx, cz), q -> new IntArrayList()).add((s << 12) | (i / 2));
			}
			if (r.a() >= 0) adj.get(r.a()).add(s);
			if (r.b() >= 0 && r.b() != r.a()) adj.get(r.b()).add(s);
		}
		tmp.forEach((k, v) -> cells.put((long) k, v.toIntArray()));
	}

	/** Nearest road centreline piece within maxDist of (x, z), or null. */
	public Hit nearest(double x, double z, double maxDist) {
		Hit best = null;
		int r = (int) Math.ceil(maxDist / CELL);
		int ccx = (int) Math.floor(x / CELL), ccz = (int) Math.floor(z / CELL);
		for (int cx = ccx - r; cx <= ccx + r; cx++)
			for (int cz = ccz - r; cz <= ccz + r; cz++) {
				int[] a = cells.get(key(cx, cz));
				if (a == null) continue;
				for (int code : a) {
					int s = code >>> 12, i = (code & 0xFFF) * 2;
					float[] p = f.roads.get(s).xz();
					double ax = p[i], az = p[i + 1], dx = p[i + 2] - ax, dz = p[i + 3] - az, l2 = dx * dx + dz * dz;
					double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
					double px = ax + t * dx, pz = az + t * dz, d = Math.hypot(x - px, z - pz);
					if (d <= maxDist && (best == null || d < best.dist)) best = new Hit(s, i / 2, d, t, px, pz);
				}
			}
		return best;
	}

	/** Road segment whose carriageway (half width) covers the column, or null. */
	public Hit roadAt(int x, int z) {
		Hit h = nearest(x + 0.5, z + 0.5, 30);
		if (h == null || h.dist > f.roads.get(h.seg).width() / 2) return null;
		return h;
	}

	/** Route along road centrelines from near a to near b (block x/z points), empty when no connection. */
	public List<int[]> route(int ax, int az, int bx, int bz) {
		Hit ha = nearest(ax, az, 400), hb = nearest(bx, bz, 400);
		if (ha == null || hb == null) return List.of();
		List<int[]> out = new ArrayList<>();
		if (ha.seg == hb.seg) {
			out.add(new int[] {ax, az});
			appendPiece(out, ha.seg, ha.piece, ha.t, hb.piece, hb.t);
			out.add(new int[] {bx, bz});
			return out;
		}
		CwFeatures.Road ra = f.roads.get(ha.seg), rb = f.roads.get(hb.seg);
		int n = adj.size();
		double[] g = new double[n];
		int[] prevSeg = new int[n];
		Arrays.fill(g, Double.MAX_VALUE);
		Arrays.fill(prevSeg, -1);
		double sa = along(ha), sb = along(hb);
		PriorityQueue<double[]> pq = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		if (ra.a() >= 0) { g[ra.a()] = sa; pq.add(new double[] {sa + heur(ra.a(), bx, bz), ra.a()}); }
		if (ra.b() >= 0 && segLen[ha.seg] - sa < g[ra.b()]) { g[ra.b()] = segLen[ha.seg] - sa; pq.add(new double[] {g[ra.b()] + heur(ra.b(), bx, bz), ra.b()}); }
		int goalA = rb.a(), goalB = rb.b();
		int end = -1;
		double bestTotal = Double.MAX_VALUE;
		int expansions = 0;
		while (!pq.isEmpty() && expansions++ < 200_000) {
			double[] top = pq.poll();
			int u = (int) top[1];
			if (top[0] - heur(u, bx, bz) > g[u] + 1e-6) continue;
			if (top[0] >= bestTotal) break;
			if (u == goalA && g[u] + sb < bestTotal) { bestTotal = g[u] + sb; end = u; }
			if (u == goalB && g[u] + segLen[hb.seg] - sb < bestTotal) { bestTotal = g[u] + segLen[hb.seg] - sb; end = u; }
			for (int s : adj.get(u)) {
				CwFeatures.Road r = f.roads.get(s);
				int v = r.a() == u ? r.b() : r.a();
				if (v < 0) continue;
				double nd = g[u] + segLen[s] * (r.kind() == 1 ? 0.8 : 1.0);
				if (nd < g[v]) { g[v] = nd; prevSeg[v] = s; pq.add(new double[] {nd + heur(v, bx, bz), v}); }
			}
		}
		if (end < 0) return List.of();
		// rebuild junction chain
		List<Integer> segs = new ArrayList<>();
		List<Integer> nodes = new ArrayList<>();
		int cur = end;
		nodes.add(cur);
		while (prevSeg[cur] >= 0) {
			int s = prevSeg[cur];
			segs.add(s);
			CwFeatures.Road r = f.roads.get(s);
			cur = r.a() == cur ? r.b() : r.a();
			nodes.add(cur);
		}
		Collections.reverse(segs);
		Collections.reverse(nodes);
		int startNode = nodes.get(0);
		out.add(new int[] {ax, az});
		// along the start segment to startNode
		if (startNode == ra.a()) appendPiece(out, ha.seg, ha.piece, ha.t, 0, 0);
		else appendPiece(out, ha.seg, ha.piece, ha.t, pieces(ha.seg) - 1, 1);
		for (int k = 0; k < segs.size(); k++) {
			int s = segs.get(k);
			boolean forward = f.roads.get(s).a() == nodes.get(k);
			appendWhole(out, s, forward);
		}
		if (end == rb.a()) appendPiece(out, hb.seg, 0, 0, hb.piece, hb.t);
		else appendPiece(out, hb.seg, pieces(hb.seg) - 1, 1, hb.piece, hb.t);
		out.add(new int[] {bx, bz});
		return out;
	}

	private int pieces(int s) { return f.roads.get(s).xz().length / 2 - 1; }

	private double along(Hit h) {
		float[] p = f.roads.get(h.seg).xz();
		double s = 0;
		for (int i = 0; i < h.piece; i++) s += Math.hypot(p[2 * i + 2] - p[2 * i], p[2 * i + 3] - p[2 * i + 1]);
		return s + h.t * Math.hypot(p[2 * h.piece + 2] - p[2 * h.piece], p[2 * h.piece + 3] - p[2 * h.piece + 1]);
	}

	private double heur(int node, int bx, int bz) {
		if (node >= f.junctions.size()) return 0;
		CwFeatures.Junction j = f.junctions.get(node);
		return Math.hypot(j.x() - bx, j.z() - bz) * 0.8;
	}

	private void appendPiece(List<int[]> out, int s, int pi, double ti, int pj, double tj) {
		float[] p = f.roads.get(s).xz();
		double ax = p[2 * pi] + (p[2 * pi + 2] - p[2 * pi]) * ti, az = p[2 * pi + 1] + (p[2 * pi + 3] - p[2 * pi + 1]) * ti;
		out.add(new int[] {(int) Math.floor(ax), (int) Math.floor(az)});
		double fi = pi + ti, fj = pj + tj;
		if (fj > fi) for (int k = pi + 1; k <= pj; k++) out.add(new int[] {(int) Math.floor(p[2 * k]), (int) Math.floor(p[2 * k + 1])});
		else for (int k = pi; k > pj; k--) out.add(new int[] {(int) Math.floor(p[2 * k]), (int) Math.floor(p[2 * k + 1])});
		double bx = p[2 * pj] + (p[2 * pj + 2] - p[2 * pj]) * tj, bz = p[2 * pj + 1] + (p[2 * pj + 3] - p[2 * pj + 1]) * tj;
		out.add(new int[] {(int) Math.floor(bx), (int) Math.floor(bz)});
	}

	private void appendWhole(List<int[]> out, int s, boolean forward) {
		float[] p = f.roads.get(s).xz();
		int n = p.length / 2;
		for (int k = 0; k < n; k++) {
			int i = forward ? k : n - 1 - k;
			out.add(new int[] {(int) Math.floor(p[2 * i]), (int) Math.floor(p[2 * i + 1])});
		}
	}
}
