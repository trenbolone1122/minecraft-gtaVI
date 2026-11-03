package dev.leonida.vicecity.gen;

import dev.leonida.core.api.GenTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One hand-built landmark site. Subclasses author a {@link Model} in local metres (u east, v north, h up from the
 * datum) and declare where it stands on the map. The site claims its footprint polygons (from buildings.json, so the
 * world module never stamps a generic box over the landmark) plus anything its model covers.
 */
public abstract class Site {
	public final String id, name;
	/** Model origin on the map (map metres, y north) and rotation (radians, counter-clockwise on the north-up map). */
	public final double mapX, mapY, theta;
	protected final List<double[][]> claimPolys = new ArrayList<>(); // map coords
	private final Map<GenTerrain, Vox> cache = new WeakHashMap<>();
	private final List<View> views = new ArrayList<>();
	public int minX, minZ, maxX, maxZ; // world bounds of the claim

	/** Camera preset in model coordinates: eye (u, h, v) looking at (tu, th, tv). */
	public record View(String name, double u, double h, double v, double tu, double th, double tv) {}

	protected Site(String id, String name, double mapX, double mapY, double theta) {
		this.id = id;
		this.name = name;
		this.mapX = mapX;
		this.mapY = mapY;
		this.theta = theta;
	}

	/** Add the buildings.json footprint(s) registered for this landmark id in footprints.json. */
	protected void claimFootprints() {
		for (double[][] p : Footprints.get(id)) claimPolys.add(p);
	}

	/** Claim a local-frame rectangle (model metres). */
	protected void claimLocalRect(double u0, double v0, double u1, double v1) {
		double[][] p = {toMap(u0, v0), toMap(u1, v0), toMap(u1, v1), toMap(u0, v1)};
		claimPolys.add(p);
	}

	/** Claim a local-frame ellipse. */
	protected void claimLocalEllipse(double rx, double ry) {
		double[][] p = new double[48][];
		for (int i = 0; i < 48; i++) p[i] = toMap(rx * Math.cos(i * Math.PI * 2 / 48), ry * Math.sin(i * Math.PI * 2 / 48));
		claimPolys.add(p);
	}

	protected void view(String name, double u, double h, double v, double tu, double th, double tv) {
		views.add(new View(name, u, h, v, tu, th, tv));
	}

	public List<View> views() {
		return views;
	}

	public double[] toMap(double u, double v) {
		double c = Math.cos(theta), s = Math.sin(theta);
		return new double[] {mapX + u * c - v * s, mapY + u * s + v * c};
	}

	/** World x, z of a model point. */
	public double[] toWorld(double u, double v) {
		double[] m = toMap(u, v);
		return new double[] {m[0], -m[1]};
	}

	/** Finish construction: compute bounds. Call at the end of the subclass constructor. */
	protected void seal() {
		minX = minZ = Integer.MAX_VALUE;
		maxX = maxZ = Integer.MIN_VALUE;
		for (double[][] p : claimPolys)
			for (double[] q : p) {
				minX = Math.min(minX, (int) Math.floor(q[0]));
				maxX = Math.max(maxX, (int) Math.ceil(q[0]));
				minZ = Math.min(minZ, (int) Math.floor(-q[1]));
				maxZ = Math.max(maxZ, (int) Math.ceil(-q[1]));
			}
	}

	public boolean claims(int x, int z) {
		if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
		double mx = x + 0.5, my = -(z + 0.5);
		for (double[][] p : claimPolys) if (inside(p, mx, my)) return true;
		return false;
	}

	public static boolean inside(double[][] p, double x, double y) {
		boolean in = false;
		for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
			if ((p[i][1] > y) != (p[j][1] > y) && x < (p[j][0] - p[i][0]) * (y - p[i][1]) / (p[j][1] - p[i][1]) + p[i][0]) in = !in;
		}
		return in;
	}

	/** Datum y (h = 0): one above the highest dry ground sampled over the claim, or one above sea level. */
	public int datum(GenTerrain t) {
		int best = Integer.MIN_VALUE;
		for (int x = minX; x <= maxX; x += 6)
			for (int z = minZ; z <= maxZ; z += 6) {
				if (!claims(x, z)) continue;
				if (t.waterY(x, z) != Integer.MIN_VALUE) continue;
				best = Math.max(best, t.groundY(x, z));
			}
		return (best == Integer.MIN_VALUE ? t.seaLevel() : best) + 1;
	}

	/** Datum of the most recent build (Integer.MIN_VALUE before the first chunk of this site was generated). */
	public volatile int lastDatum = Integer.MIN_VALUE;

	/** Fill under the lowest block of each column (smooth stone; beach sites use sand). */
	protected net.minecraft.world.level.block.state.BlockState foundation() {
		return Pal.foundation();
	}

	/** Author the landmark. */
	protected abstract void build(Model m);

	/** Built world-space blocks for this terrain (built once, then cached). */
	public Vox vox(GenTerrain t) {
		synchronized (cache) {
			Vox v = cache.get(t);
			if (v != null) return v;
			Model m = new Model();
			build(m);
			v = new Vox();
			double[] o = toWorld(0, 0);
			lastDatum = datum(t);
			m.place(v, o[0], o[1], theta, lastDatum, t, foundation());
			cache.put(t, v);
			return v;
		}
	}

	public void forget() {
		synchronized (cache) {
			cache.clear();
		}
	}
}
