package dev.leonida.vicecity.gen;

import dev.leonida.core.api.GenTerrain;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A landmark authored in its own local frame, in metres = blocks: u east, v north (map axes, so pipeline coordinates
 * port directly), h up from the site datum (h = 0 is the first block above the datum ground). Directions in model
 * space use Minecraft names with NORTH = +v, EAST = +u. {@link #place} rotates the model by any map angle into world
 * space (nearest-cell resampling, no holes) and adds foundations down to the terrain.
 */
public final class Model {
	private static final int H0 = 128; // array offset: h may go down to -128 (basements, piles)
	private final Long2ObjectOpenHashMap<BlockState[]> cols = new Long2ObjectOpenHashMap<>();
	public int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE, maxH = 0;

	private static long ck(int u, int v) {
		return ((long) u << 32) ^ (v & 0xffffffffL);
	}

	public void set(int u, int h, int v, BlockState s) {
		if (s == null || h < -H0) return;
		long k = ck(u, v);
		BlockState[] c = cols.get(k);
		int i = h + H0;
		if (c == null) {
			c = new BlockState[Math.max(i + 8, H0 + 16)];
			cols.put(k, c);
		} else if (i >= c.length) {
			c = java.util.Arrays.copyOf(c, Math.max(i + 8, c.length * 3 / 2));
			cols.put(k, c);
		}
		c[i] = s;
		if (u < minU) minU = u;
		if (u > maxU) maxU = u;
		if (v < minV) minV = v;
		if (v > maxV) maxV = v;
		if (h > maxH) maxH = h;
	}

	/** Set only if empty. */
	public void add(int u, int h, int v, BlockState s) {
		if (get(u, h, v) == null) set(u, h, v, s);
	}

	public BlockState get(int u, int h, int v) {
		BlockState[] c = cols.get(ck(u, v));
		int i = h + H0;
		return c == null || i < 0 || i >= c.length ? null : c[i];
	}

	public boolean has(int u, int v) {
		return cols.containsKey(ck(u, v));
	}

	public void box(int u0, int h0, int v0, int u1, int h1, int v1, BlockState s) {
		for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
			for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
				for (int h = Math.min(h0, h1); h <= Math.max(h0, h1); h++) set(u, h, v, s);
	}

	/** Hollow box walls (no top or bottom). */
	public void walls(int u0, int h0, int v0, int u1, int h1, int v1, BlockState s) {
		box(u0, h0, v0, u1, h1, v0, s);
		box(u0, h0, v1, u1, h1, v1, s);
		box(u0, h0, v0, u0, h1, v1, s);
		box(u1, h0, v0, u1, h1, v1, s);
	}

	/** 3D line of blocks (Bresenham-like DDA) with a square cross-section of side r*2+1. */
	public void line(double u0, double h0, double v0, double u1, double h1, double v1, int r, BlockState s) {
		double du = u1 - u0, dh = h1 - h0, dv = v1 - v0;
		int n = (int) Math.ceil(Math.max(Math.abs(du), Math.max(Math.abs(dh), Math.abs(dv))) * 2) + 1;
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			int u = (int) Math.floor(u0 + du * t), h = (int) Math.floor(h0 + dh * t), v = (int) Math.floor(v0 + dv * t);
			if (r == 0) set(u, h, v, s);
			else for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) for (int c = -r; c <= r; c++) set(u + a, h + b, v + c, s);
		}
	}

	/** Horizontal line on one level, 1 block thick, filling diagonal gaps (4-connected) so walls stay closed. */
	public void hline(double u0, double v0, double u1, double v1, int h0, int h1, BlockState s) {
		double du = u1 - u0, dv = v1 - v0;
		int n = (int) Math.ceil(Math.max(Math.abs(du), Math.abs(dv)) * 3) + 1;
		int pu = Integer.MIN_VALUE, pv = 0;
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			int u = (int) Math.floor(u0 + du * t), v = (int) Math.floor(v0 + dv * t);
			if (pu != Integer.MIN_VALUE && u != pu && v != pv) for (int h = h0; h <= h1; h++) set(u, h, pv, s);
			for (int h = h0; h <= h1; h++) set(u, h, v, s);
			pu = u;
			pv = v;
		}
	}

	public Iterable<Long2ObjectMap.Entry<BlockState[]>> columns() {
		return cols.long2ObjectEntrySet();
	}

	public static Direction rotate(Direction d, int quarters) {
		Direction r = d;
		for (int i = 0; i < ((quarters % 4) + 4) % 4; i++) r = r.getCounterClockWise();
		return r;
	}

	/**
	 * Write the model into world space. The model origin (u = 0, v = 0) lands on world (ox, oz) (continuous), rotated by
	 * {@code theta} radians counter-clockwise on the north-up map. h = 0 lands on world y {@code y0}. Columns with model
	 * content get terrain cleared above y0 and a foundation below their lowest block.
	 */
	public void place(Vox out, double ox, double oz, double theta, int y0, GenTerrain terrain, BlockState foundation) {
		double c = Math.cos(theta), s = Math.sin(theta);
		int quarters = (int) Math.round(theta / (Math.PI / 2));
		Rotation rot = switch (((quarters % 4) + 4) % 4) {
			case 1 -> Rotation.COUNTERCLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.CLOCKWISE_90;
			default -> Rotation.NONE;
		};
		boolean exact = Math.abs(theta - quarters * Math.PI / 2) < 1e-6;
		if (exact) {
			c = Math.round(c);
			s = Math.round(s);
		}
		// world bbox of the model corners
		double[][] corners = {{minU, minV}, {maxU + 1, minV}, {minU, maxV + 1}, {maxU + 1, maxV + 1}};
		double wx0 = 1e18, wx1 = -1e18, wz0 = 1e18, wz1 = -1e18;
		for (double[] p : corners) {
			double x = ox + p[0] * c - p[1] * s, z = oz - (p[0] * s + p[1] * c);
			wx0 = Math.min(wx0, x);
			wx1 = Math.max(wx1, x);
			wz0 = Math.min(wz0, z);
			wz1 = Math.max(wz1, z);
		}
		java.util.IdentityHashMap<BlockState, BlockState> rotated = new java.util.IdentityHashMap<>();
		for (int x = (int) Math.floor(wx0) - 1; x <= (int) Math.ceil(wx1) + 1; x++) {
			for (int z = (int) Math.floor(wz0) - 1; z <= (int) Math.ceil(wz1) + 1; z++) {
				double dx = x + 0.5 - ox, dz = z + 0.5 - oz;
				int u = (int) Math.floor(c * dx - s * dz), v = (int) Math.floor(-s * dx - c * dz);
				BlockState[] col = cols.get(ck(u, v));
				if (col == null) continue;
				int lowest = Integer.MAX_VALUE;
				for (int i = 0; i < col.length; i++) {
					BlockState b = col[i];
					if (b == null) continue;
					if (!b.isAir() && lowest == Integer.MAX_VALUE) lowest = i - H0;
					BlockState r = rot == Rotation.NONE ? b : rotated.computeIfAbsent(b, k -> k.rotate(rot));
					out.set(x, y0 + i - H0, z, r);
				}
				int g = terrain.groundY(x, z);
				for (int y = y0; y <= g; y++) if (out.get(x, y, z) == null) out.set(x, y, z, Pal.AIR);
				if (lowest != Integer.MAX_VALUE && foundation != null) {
					int top = y0 + lowest - 1;
					int wy = terrain.waterY(x, z);
					int bottom = Math.min(g, wy == Integer.MIN_VALUE ? g : g) + 1;
					for (int y = Math.max(bottom, top - 40); y <= top; y++) if (out.get(x, y, z) == null) out.set(x, y, z, foundation);
				}
			}
		}
	}
}
