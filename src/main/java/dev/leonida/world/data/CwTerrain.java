package dev.leonida.world.data;

import dev.leonida.core.api.GenTerrain;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Terrain baked from community-world-v1 (tools/worldgen/cw_terrain_pack.py): per 2 m cell a class
 * byte, a water depth and a height (land ground or water surface, decimetres). MC x = map x,
 * z = -map y, sea level 0 m = Y {@link #SEA} (top water block).
 */
public final class CwTerrain implements GenTerrain {
	public static final int SEA = 62;
	/** Land classes. */
	public static final int GRASS = 1, SAND = 2, PAVING = 3, DIRT = 4;
	/** Water kinds (class = WATER + kind). */
	public static final int WATER = 8;
	public static final int W_SHALLOW = 0, W_BAY = 1, W_CHANNEL = 2, W_DEEP = 3, W_SWAMP = 4, W_LAKE = 5, W_OCEAN = 6;

	private static volatile CwTerrain instance;

	public static CwTerrain get() {
		CwTerrain t = instance;
		if (t == null) {
			synchronized (CwTerrain.class) {
				if (instance == null) instance = new CwTerrain();
				t = instance;
			}
		}
		return t;
	}

	private final byte[] file;
	private final int ox, oz, cell, r, rw, rh, dataStart;
	private final int[] off, len;
	private final ConcurrentHashMap<Integer, SoftReference<Region>> cache = new ConcurrentHashMap<>();

	private record Region(byte[] cls, byte[] depth, short[] h) {}

	private CwTerrain() {
		try (InputStream in = CwTerrain.class.getResourceAsStream("/data/leonida/world/terrain.bin")) {
			if (in == null) throw new IllegalStateException("missing /data/leonida/world/terrain.bin");
			file = in.readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		ByteBuffer b = ByteBuffer.wrap(file);
		if (b.getInt() != 0x4C545231) throw new IllegalStateException("bad terrain.bin");
		ox = b.getInt(); oz = b.getInt(); cell = b.getInt(); r = b.getInt(); rw = b.getInt(); rh = b.getInt();
		off = new int[rw * rh]; len = new int[rw * rh];
		for (int i = 0; i < off.length; i++) { off[i] = b.getInt(); len[i] = b.getInt(); }
		dataStart = b.position();
	}

	public int minX() { return ox; }
	public int minZ() { return oz; }
	public int maxX() { return ox + rw * r * cell; }
	public int maxZ() { return oz + rh * r * cell; }

	private Region region(int ri, int rj) {
		int key = rj * rw + ri;
		SoftReference<Region> ref = cache.get(key);
		Region reg = ref == null ? null : ref.get();
		if (reg != null) return reg;
		int n = r * r;
		byte[] raw = new byte[n * 4];
		Inflater inf = new Inflater();
		try {
			inf.setInput(file, dataStart + off[key], len[key]);
			int got = 0;
			while (got < raw.length && !inf.finished()) got += inf.inflate(raw, got, raw.length - got);
		} catch (DataFormatException e) {
			throw new IllegalStateException(e);
		} finally {
			inf.end();
		}
		byte[] c = new byte[n], d = new byte[n];
		System.arraycopy(raw, 0, c, 0, n);
		System.arraycopy(raw, n, d, 0, n);
		short[] h = new short[n];
		for (int j = 0; j < r; j++) {
			int acc = 0;
			for (int i = 0; i < r; i++) {
				int k = j * r + i, p = 2 * n + 2 * k;
				acc += (short) (((raw[p] & 0xFF) << 8) | (raw[p + 1] & 0xFF));
				h[k] = (short) acc;
			}
		}
		reg = new Region(c, d, h);
		cache.put(key, new SoftReference<>(reg));
		return reg;
	}

	/** Cell lookups; ci/cj in cell units. Outside the map: deep ocean at sea level. */
	private int cellCls(int ci, int cj) {
		if (ci < 0 || cj < 0 || ci >= rw * r || cj >= rh * r) return WATER + W_DEEP;
		Region g = region(ci / r, cj / r);
		return g.cls[(cj % r) * r + (ci % r)];
	}

	private int cellDepth(int ci, int cj) {
		if (ci < 0 || cj < 0 || ci >= rw * r || cj >= rh * r) return 30;
		Region g = region(ci / r, cj / r);
		return g.depth[(cj % r) * r + (ci % r)];
	}

	private int cellH(int ci, int cj) {
		if (ci < 0 || cj < 0 || ci >= rw * r || cj >= rh * r) return 0;
		Region g = region(ci / r, cj / r);
		return g.h[(cj % r) * r + (ci % r)];
	}

	private int ci(int x) { return Math.floorDiv(x - ox, cell); }
	private int cj(int z) { return Math.floorDiv(z - oz, cell); }

	/** Class at block: GRASS/SAND/PAVING/DIRT or WATER + kind. */
	public int cls(int x, int z) { return cellCls(ci(x), cj(z)); }

	public static boolean isWater(int c) { return c >= WATER; }

	/**
	 * Land class with smooth contours: the 2 m class cells are blended with a 4 m tent filter and the
	 * class with the largest weight wins, so paving, sand and grass edges follow lines and curves at
	 * block resolution instead of 2 block stair steps. Water and cells near water keep the raw class.
	 */
	public int clsSmooth(int x, int z) {
		int raw = cls(x, z);
		if (isWater(raw)) return raw;
		double fx = (x + 0.5 - ox) / cell - 0.5, fz = (z + 0.5 - oz) / cell - 0.5;
		int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fz);
		double[] w = new double[WATER];
		for (int j = j0 - 1; j <= j0 + 2; j++) {
			double wz = Math.max(0, 1 - Math.abs(j - fz) / 2);
			if (wz <= 0) continue;
			for (int i = i0 - 1; i <= i0 + 2; i++) {
				double wx = Math.max(0, 1 - Math.abs(i - fx) / 2);
				if (wx <= 0) continue;
				int c = cellCls(i, j);
				if (isWater(c) || c < 0) return raw;
				w[c] += wx * wz;
			}
		}
		int best = raw;
		for (int c = 0; c < WATER; c++) if (w[c] > w[best] + 1e-9) best = c;
		return best;
	}

	public boolean water(int x, int z) { return cls(x, z) >= WATER; }

	/** Water depth in blocks (0 on land). */
	public int depth(int x, int z) { return cellDepth(ci(x), cj(z)); }

	/** Smooth land height in metres (bilinear over land cells only). */
	public double landHeightM(int x, int z) {
		double fx = (x + 0.5 - ox) / cell - 0.5, fz = (z + 0.5 - oz) / cell - 0.5;
		int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fz);
		double tx = fx - i0, tz = fz - j0;
		double sum = 0, wsum = 0;
		for (int dj = 0; dj <= 1; dj++)
			for (int di = 0; di <= 1; di++) {
				if (isWater(cellCls(i0 + di, j0 + dj))) continue;
				double w = (di == 0 ? 1 - tx : tx) * (dj == 0 ? 1 - tz : tz);
				sum += w * cellH(i0 + di, j0 + dj);
				wsum += w;
			}
		if (wsum < 1e-6) return cellH(ci(x), cj(z)) / 10.0;
		return sum / wsum / 10.0;
	}

	/** Water surface Y at a water column. */
	public int waterSurfaceY(int x, int z) {
		return SEA + Math.round(cellH(ci(x), cj(z)) / 10f);
	}

	/** Top natural block Y: land ground or seabed. */
	@Override
	public int groundY(int x, int z) {
		if (water(x, z)) return waterSurfaceY(x, z) - Math.max(1, depth(x, z));
		return landY(x, z);
	}

	/** Top land block Y (ignores water). */
	public int landY(int x, int z) {
		return Math.max(SEA + 1, SEA + (int) Math.round(landHeightM(x, z)));
	}

	@Override
	public int waterY(int x, int z) {
		return water(x, z) ? waterSurfaceY(x, z) : Integer.MIN_VALUE;
	}

	@Override
	public int seaLevel() { return SEA; }

	/** Distance-free check: any water cell within r blocks (square ring sample). */
	public boolean nearWater(int x, int z, int rad) {
		for (int dx = -rad; dx <= rad; dx += Math.max(1, rad))
			for (int dz = -rad; dz <= rad; dz += Math.max(1, rad))
				if (water(x + dx, z + dz)) return true;
		return false;
	}
}
