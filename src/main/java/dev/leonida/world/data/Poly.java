package dev.leonida.world.data;

import java.util.List;

/** Polygon helpers. Vertices are block-corner coordinates (map metres); test points are usually block centres (x + 0.5). */
public final class Poly {
	private Poly() {}

	/** Even-odd point in polygon test. */
	public static boolean contains(List<int[]> poly, double x, double z) {
		boolean in = false;
		int n = poly.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = poly.get(i)[0], zi = poly.get(i)[1], xj = poly.get(j)[0], zj = poly.get(j)[1];
			if ((zi > z) != (zj > z)) {
				double xc = (xj - xi) * (z - zi) / (zj - zi) + xi;
				if (x < xc) in = !in;
			}
		}
		return in;
	}

	/** Distance from a point to the polygon boundary. */
	public static double edgeDistance(List<int[]> poly, double x, double z) {
		double best = Double.MAX_VALUE;
		int n = poly.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = poly.get(i)[0], zi = poly.get(i)[1], xj = poly.get(j)[0], zj = poly.get(j)[1];
			double dx = xj - xi, dz = zj - zi, l2 = dx * dx + dz * dz;
			double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - xi) * dx + (z - zi) * dz) / l2));
			double px = xi + t * dx - x, pz = zi + t * dz - z;
			best = Math.min(best, px * px + pz * pz);
		}
		return Math.sqrt(best);
	}
}
