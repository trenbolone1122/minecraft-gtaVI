package dev.leonida.build;

import java.util.List;

/** Polygon helpers. Polygon vertices are block coordinates; blocks on an edge count as inside. */
final class Geo {
	private Geo() {}

	static boolean inside(List<int[]> poly, int x, int z) {
		int n = poly.size();
		boolean in = false;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			int xi = poly.get(i)[0], zi = poly.get(i)[1], xj = poly.get(j)[0], zj = poly.get(j)[1];
			if (onSegment(xi, zi, xj, zj, x, z)) return true;
			if ((zi > z) != (zj > z)) {
				double xc = xi + (double) (z - zi) * (xj - xi) / (zj - zi);
				if (x < xc) in = !in;
			}
		}
		return in;
	}

	private static boolean onSegment(int ax, int az, int bx, int bz, int px, int pz) {
		if (px < Math.min(ax, bx) - 0.5 || px > Math.max(ax, bx) + 0.5 || pz < Math.min(az, bz) - 0.5 || pz > Math.max(az, bz) + 0.5) return false;
		double dx = bx - ax, dz = bz - az;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len == 0) return px == ax && pz == az;
		double dist = Math.abs(dz * (px - ax) - dx * (pz - az)) / len;
		return dist <= 0.5;
	}

	static List<int[]> rect(int x0, int z0, int x1, int z1) {
		return List.of(new int[] {x0, z0}, new int[] {x0, z1}, new int[] {x1, z1}, new int[] {x1, z0});
	}
}
