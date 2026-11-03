package dev.leonida.vicecity.gen;

import net.minecraft.core.Direction;

/**
 * Distance field around a playing surface, for seating bowls that hug any field shape (rounded court rectangle,
 * baseball fan). Grid in local metres; distance in metres outward from the field edge (0 inside), via a 5-7-11 chamfer
 * transform (error under 2%).
 */
public final class Bowl {
	public interface Field {
		boolean in(double u, double v);
	}

	private final int u0, v0, w, d;
	private final float[] dist;

	public Bowl(int u0, int v0, int u1, int v1, Field field) {
		this.u0 = u0;
		this.v0 = v0;
		this.w = u1 - u0 + 1;
		this.d = v1 - v0 + 1;
		int[] g = new int[w * d];
		final int INF = 1 << 28;
		for (int j = 0; j < d; j++)
			for (int i = 0; i < w; i++) g[j * w + i] = field.in(u0 + i + 0.5, v0 + j + 0.5) ? 0 : INF;
		int[][] fwd = {{-1, 0, 5}, {0, -1, 5}, {-1, -1, 7}, {1, -1, 7}, {-1, -2, 11}, {1, -2, 11}, {-2, -1, 11}, {2, -1, 11}};
		for (int j = 0; j < d; j++)
			for (int i = 0; i < w; i++) {
				int best = g[j * w + i];
				for (int[] k : fwd) {
					int ii = i + k[0], jj = j + k[1];
					if (ii >= 0 && ii < w && jj >= 0 && jj < d) best = Math.min(best, g[jj * w + ii] + k[2]);
				}
				g[j * w + i] = best;
			}
		for (int j = d - 1; j >= 0; j--)
			for (int i = w - 1; i >= 0; i--) {
				int best = g[j * w + i];
				for (int[] k : fwd) {
					int ii = i - k[0], jj = j - k[1];
					if (ii >= 0 && ii < w && jj >= 0 && jj < d) best = Math.min(best, g[jj * w + ii] + k[2]);
				}
				g[j * w + i] = best;
			}
		dist = new float[w * d];
		for (int i = 0; i < g.length; i++) dist[i] = g[i] / 5f;
	}

	/** Distance in metres from the field edge at cell (u, v) (0 inside the field, huge outside the grid). */
	public float s(int u, int v) {
		int i = u - u0, j = v - v0;
		if (i < 0 || j < 0 || i >= w || j >= d) return 1e9f;
		return dist[j * w + i];
	}

	/** Cardinal direction pointing away from the field (seat backs face this way). */
	public Direction outward(int u, int v) {
		float du = s(u + 1, v) - s(u - 1, v), dv = s(u, v + 1) - s(u, v - 1);
		if (Math.abs(du) >= Math.abs(dv)) return du >= 0 ? Direction.EAST : Direction.WEST;
		return dv >= 0 ? Direction.NORTH : Direction.SOUTH;
	}
}
