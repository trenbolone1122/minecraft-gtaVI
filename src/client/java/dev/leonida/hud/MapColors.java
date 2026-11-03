package dev.leonida.hud;

import dev.leonida.core.api.MapQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Cache of map colours per block, in 64x64 regions, filled on the client thread with a per frame budget.
 * Source is {@link MapQuery#mapColor}; while the flat placeholder is installed it falls back to the real
 * client world's top block colours (so the minimap shows actual terrain during development).
 * Technique note: like Xaero's region cache, colours are computed once per region, not per frame.
 */
public final class MapColors {
	private MapColors() {}

	static final int SHIFT = 6, SIZE = 1 << SHIFT, MASK = SIZE - 1;
	/** Placeholder for "not computed yet". */
	public static final int UNKNOWN = 0x00000000;
	private static final int MAX_REGIONS = 2048;

	private static final class Region {
		final int[] c = new int[SIZE * SIZE];
		boolean complete;
		long lastTry;
	}

	private static final Map<Long, Region> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Region> e) { return size() > MAX_REGIONS; }
	};
	private static int budget;
	/** Lookups since the last {@link #frame} call that hit a region not computed yet. */
	public static int misses;
	/** Current mip level: a region covers SIZE << lod blocks, sampled every 1 << lod blocks. */
	private static int lod;
	private static Object lastLevel;
	private static MapQuery lastQuery;
	private static long lastKey = Long.MIN_VALUE;
	private static Region lastRegion;

	/** Call once per frame before sampling: sets how many regions may be computed this frame. */
	public static void frame(int regionBudget) { frame(regionBudget, 1.0); }

	/**
	 * Like {@link #frame(int)}, with the view scale in blocks per output pixel: when zoomed out, regions
	 * sample every 2^lod blocks (a mip level), so the whole island can be shown without evaluating every column.
	 */
	public static void frame(int regionBudget, double blocksPerPixel) {
		budget = regionBudget;
		misses = 0;
		int l = 0;
		if (MapQuery.get() != MapQuery.FLAT && onLeonida())
			while (l < 7 && (2 << l) <= blocksPerPixel) l++;
		if (l != lod) { lod = l; lastKey = Long.MIN_VALUE; lastRegion = null; }
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != lastLevel || MapQuery.get() != lastQuery) {
			CACHE.clear();
			lastRegion = null;
			lastKey = Long.MIN_VALUE;
			lastLevel = mc.level;
			lastQuery = MapQuery.get();
		}
	}

	/** ARGB map colour at a block, or {@link #UNKNOWN} if not computed yet. */
	public static int get(int x, int z) {
		int sh = SHIFT + lod;
		int rx = x >> sh, rz = z >> sh;
		long key = ((long) lod << 59) ^ ((long) (rx & 0x3FFFFFFF) << 29) ^ (rz & 0x1FFFFFFFL);
		Region r;
		if (key == lastKey) {
			r = lastRegion;
		} else {
			r = CACHE.get(key);
			long now = System.currentTimeMillis();
			if ((r == null || (!r.complete && now - r.lastTry > 1500)) && budget > 0) {
				budget--;
				if (r == null) { r = new Region(); CACHE.put(key, r); }
				fill(r, rx << sh, rz << sh, lod);
				r.lastTry = now;
			}
			lastKey = key;
			lastRegion = r;
		}
		if (r == null) { misses++; return UNKNOWN; }
		return r.c[(((z >> lod) & MASK) << SHIFT) | ((x >> lod) & MASK)];
	}

	private static void fill(Region r, int x0, int z0, int lod) {
		MapQuery q = MapQuery.get();
		if (q != MapQuery.FLAT && onLeonida()) {
			try {
				for (int dz = 0; dz < SIZE; dz++)
					for (int dx = 0; dx < SIZE; dx++)
						r.c[(dz << SHIFT) | dx] = q.mapColor(x0 + (dx << lod) + (1 << lod >> 1), z0 + (dz << lod) + (1 << lod >> 1)) | 0xFF000000;
				r.complete = true;
			} catch (RuntimeException e) {
				r.complete = false;
			}
			return;
		}
		fillFromWorld(r, x0, z0);
	}

	/** True when the current level uses the Leonida dimension type (the Leonida preset overworld or the Leonida dimension). */
	public static boolean onLeonida() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return false;
		return level.dimensionTypeRegistration().unwrapKey()
				.map(k -> k.identifier().getNamespace().equals("leonida")).orElse(false);
	}

	private static void fillFromWorld(Region r, int x0, int z0) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return;
		boolean complete = true;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] prevRowH = new int[SIZE];
		for (int dx = 0; dx < SIZE; dx++) prevRowH[dx] = Integer.MIN_VALUE;
		for (int dz = -1; dz < SIZE; dz++) {
			for (int dx = 0; dx < SIZE; dx++) {
				int x = x0 + dx, z = z0 + dz;
				LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
				if (chunk == null) {
					if (dz >= 0) { r.c[(dz << SHIFT) | dx] = UNKNOWN; complete = false; }
					prevRowH[dx] = Integer.MIN_VALUE;
					continue;
				}
				int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				if (dz >= 0) {
					pos.set(x, h, z);
					BlockState s = chunk.getBlockState(pos);
					int y = h;
					while (y > level.getMinY() && s.getMapColor(level, pos) == MapColor.NONE) {
						pos.setY(--y);
						s = chunk.getBlockState(pos);
					}
					MapColor mc = s.getMapColor(level, pos);
					int rgb = mc.col;
					float shade;
					if (mc == MapColor.WATER) {
						int depth = 0;
						BlockPos.MutableBlockPos p2 = pos.mutable();
						while (depth < 12 && chunk.getBlockState(p2.move(0, -1, 0)).getFluidState().isEmpty() == false) depth++;
						shade = 1.05f - depth * 0.03f;
					} else {
						int ph = prevRowH[dx];
						shade = ph == Integer.MIN_VALUE || h == ph ? 0.93f : (h > ph ? 1.05f : 0.82f);
					}
					r.c[(dz << SHIFT) | dx] = 0xFF000000 | scale(rgb, shade);
				}
				prevRowH[dx] = h;
			}
		}
		r.complete = complete;
	}

	static int scale(int rgb, float f) {
		int rr = Math.min(255, (int) (((rgb >> 16) & 255) * f));
		int gg = Math.min(255, (int) (((rgb >> 8) & 255) * f));
		int bb = Math.min(255, (int) ((rgb & 255) * f));
		return (rr << 16) | (gg << 8) | bb;
	}
}
