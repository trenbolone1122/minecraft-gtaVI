package dev.leonida.landmark.gen;

import dev.leonida.core.api.GenTerrain;

/**
 * Stand-in terrain for flat test worlds before the world module is merged: land one block above the water surface,
 * water on one side of a straight shoreline (through (sx, sz), water where the normal dot is negative).
 */
public final class FakeTerrain implements GenTerrain {
	private final int base;
	private final double sx, sz, nx, nz;

	/** @param base flat world top block y (water surface); land top is base + 1, seabed base - 2 */
	public FakeTerrain(int base, double sx, double sz, double nx, double nz) {
		this.base = base;
		this.sx = sx;
		this.sz = sz;
		double l = Math.sqrt(nx * nx + nz * nz);
		this.nx = nx / l;
		this.nz = nz / l;
	}

	/** Signed distance from the shore, positive on land. */
	public double shore(int x, int z) {
		return (x + 0.5 - sx) * nx + (z + 0.5 - sz) * nz;
	}

	public boolean isWater(int x, int z) {
		return shore(x, z) < 0;
	}

	@Override
	public int groundY(int x, int z) {
		double d = shore(x, z);
		if (d >= 0) return base + 1;
		return d > -3 ? base - 1 : base - 2;
	}

	@Override
	public int waterY(int x, int z) {
		return isWater(x, z) ? base : Integer.MIN_VALUE;
	}

	@Override
	public int seaLevel() {
		return base;
	}
}
