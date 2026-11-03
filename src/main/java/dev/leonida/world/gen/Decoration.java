package dev.leonida.world.gen;

import dev.leonida.core.api.LandmarkGenerator;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.look.LookBlocks;
import dev.leonida.world.data.CwFeatures;
import dev.leonida.world.data.CwTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Vegetation: every trees.json tree plus the coastal palm restoration at its surveyed spot (royal
 * palms, coconut palms, mangroves, cypress, pines), then dense procedural fill (palms and oaks in
 * yards and parks, pines and cypress inland, mangroves on swamp edges) and ground cover.
 */
final class Decoration {
	private Decoration() {}

	static void decorate(CwTerrain t, WorldGenLevel level, ChunkAccess chunk) {
		ChunkPos cp = chunk.getPos();
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		RoadGenerator roads = RoadGenerator.get();
		CwFeatures f = CwFeatures.get();
		for (CwFeatures.Tree tr : f.treesInChunk(cp.x(), cp.z())) {
			int x = tr.x(), z = tr.z();
			if (blocked(t, f, roads, x, z)) continue;
			boolean wet = t.water(x, z);
			if (wet && tr.kind() != 1) continue;
			int y = (wet ? t.waterSurfaceY(x, z) : t.landY(x, z)) + 1;
			if (!wet && !natural(level, pos, x, y - 1, z)) continue;
			int kind = tr.kind();
			if (!wet && kind != 0 && kind != 4 && (t.cls(x, z) == CwTerrain.SAND || t.nearWater(x, z, 12)) && !swampy(t, x, z)) kind = 4;
			else if (kind == 1 && !wet && !swampy(t, x, z)) {
				// Broadleaf/mangrove points away from swamps: palms on the coast and in town (Miami look).
				if (t.cls(x, z) == CwTerrain.SAND || t.nearWater(x, z, 12)) kind = 4;
				else if (x > 1500 || nearPaving(t, x, z)) kind = 0;
			}
			tree(level, pos, kind, x, y, z, tr.height());
		}
		// Royal palms on boulevard medians and wide sidewalks, at spots the road module picks.
		try {
			for (dev.leonida.road.api.RoadSpots.Palm sp : dev.leonida.road.api.RoadSpots.palms(cp.x(), cp.z())) {
				if (dev.leonida.core.api.LandmarkGenerator.anyClaims(sp.x(), sp.z())) continue;
				lookPalm(level, LookBlocks.Palm.ROYAL, sp.x(), sp.y() + 1, sp.z(), 12 + (int) (Hash.h01(sp.x(), sp.z(), 7) * 8));
			}
		} catch (RuntimeException e) {
			// Road data problems must never break chunk generation.
		}
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				int x = x0 + lx, z = z0 + lz;
				int c = t.cls(x, z);
				double r = Hash.h01(x, z, 1234);
				if (CwTerrain.isWater(c)) {
					int kind = c - CwTerrain.WATER;
					if (kind == CwTerrain.W_SWAMP && x > VICE_BEACH_X) kind = CwTerrain.W_SHALLOW; // ocean edge tagged swamp in the data
					int wy = t.waterSurfaceY(x, z);
					if ((kind == CwTerrain.W_SWAMP || kind == CwTerrain.W_LAKE) && r < 0.05 && level.getBlockState(pos.set(x, wy + 1, z)).isAir()
							&& level.getBlockState(pos.set(x, wy, z)).is(net.minecraft.world.level.block.Blocks.WATER))
						level.setBlock(pos.set(x, wy + 1, z), WorldBlocks.LILY, 2);
					else if ((kind == CwTerrain.W_SHALLOW || kind == CwTerrain.W_BAY || kind == CwTerrain.W_SWAMP) && r > 0.85) {
						int bed = t.groundY(x, z);
						if (bed < wy - 1 && level.getBlockState(pos.set(x, bed + 1, z)).is(net.minecraft.world.level.block.Blocks.WATER))
							level.setBlock(pos.set(x, bed + 1, z), WorldBlocks.SEAGRASS, 2);
					}
					if (kind == CwTerrain.W_SWAMP && r < 0.012 && !blocked(t, f, roads, x, z) && t.depth(x, z) <= 2)
						tree(level, pos, 1, x, wy + 1, z, 9);
					continue;
				}
				if (c != CwTerrain.GRASS && c != CwTerrain.DIRT && c != CwTerrain.SAND) continue;
				int y = t.landY(x, z);
				if (!natural(level, pos, x, y, z) || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) continue;
				if (blocked(t, f, roads, x, z)) continue;
				boolean city = t.cls(x + 12, z) == CwTerrain.PAVING || t.cls(x - 12, z) == CwTerrain.PAVING
						|| t.cls(x, z + 12) == CwTerrain.PAVING || t.cls(x, z - 12) == CwTerrain.PAVING;
				boolean shore = t.nearWater(x, z, 5);
				if (c == CwTerrain.SAND) {
					if (!shore && r < 0.0025 && clearAround(t, f, roads, x, z, 3)) tree(level, pos, 4, x, y + 1, z, 9 + (int) (r * 4000) % 6);
					continue;
				}
				double treeP = city ? 0.0012 : 0.0015; // open Florida land, not forest
				if (r < treeP && clearAround(t, f, roads, x, z, 3)) {
					double k = Hash.h01(x, z, 77);
					int h = 8 + (int) (Hash.h01(x, z, 78) * 10);
					int kind;
					if (shore) kind = swampy(t, x, z) ? 1 : 4;
					else if (x > 1500) kind = k < 0.6 ? 0 : k < 0.85 ? 4 : 6;
					else if (city) kind = k < 0.6 ? 0 : k < 0.8 ? 6 : 4;
					else if (x < -2500) kind = k < 0.45 ? 3 : k < 0.75 ? 2 : k < 0.9 ? 5 : 0;
					else kind = k < 0.3 ? 6 : k < 0.45 ? 0 : k < 0.6 ? 5 : k < 0.8 ? 3 : 2;
					tree(level, pos, kind, x, y + 1, z, kind == 1 ? 9 : h);
					continue;
				}
				if (r < 0.02 && !city) level.setBlock(pos.set(x, y + 1, z), WorldBlocks.SHRUB, 2);
				else if (r < 0.03) level.setBlock(pos.set(x, y + 1, z), WorldBlocks.BUSH, 2);
				// no short grass or ferns: plain grass blocks, the map is not a vanilla meadow
			}
	}

	/** Swamp or lake water close by: mangroves there, coconut palms on every other shore. */
	/** East of this is the Vice Beach barrier island: ocean and bay shores, no swamps or mangroves. */
	private static final int VICE_BEACH_X = 1500;

	private static boolean swampy(CwTerrain t, int x, int z) {
		if (x > VICE_BEACH_X) return false;
		for (int dx = -6; dx <= 6; dx += 3)
			for (int dz = -6; dz <= 6; dz += 3) {
				int c = t.cls(x + dx, z + dz);
				if (c == CwTerrain.WATER + CwTerrain.W_SWAMP || c == CwTerrain.WATER + CwTerrain.W_LAKE) return true;
			}
		return false;
	}

	private static boolean nearPaving(CwTerrain t, int x, int z) {
		return t.cls(x + 12, z) == CwTerrain.PAVING || t.cls(x - 12, z) == CwTerrain.PAVING
				|| t.cls(x, z + 12) == CwTerrain.PAVING || t.cls(x, z - 12) == CwTerrain.PAVING;
	}

	private static boolean blocked(CwTerrain t, CwFeatures f, RoadGenerator roads, int x, int z) {
		return roads.claims(x, z) || LandmarkGenerator.anyClaims(x, z) || f.buildingAt(x, z) != null;
	}

	private static boolean clearAround(CwTerrain t, CwFeatures f, RoadGenerator roads, int x, int z, int r) {
		return !blocked(t, f, roads, x + r, z) && !blocked(t, f, roads, x - r, z) && !blocked(t, f, roads, x, z + r) && !blocked(t, f, roads, x, z - r);
	}

	/** Natural ground block at (x, y, z), so trees never sprout from roads, decks or roofs. */
	private static boolean natural(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		BlockState s = level.getBlockState(pos.set(x, y, z));
		return s == WorldBlocks.GRASS || s == WorldBlocks.DIRT || s == WorldBlocks.COARSE_DIRT || s == WorldBlocks.MUD
				|| s.getBlock() == WorldBlocks.beachSand().getBlock() || s.getBlock() == WorldBlocks.wetSand().getBlock();
	}

	private static void set(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState s, boolean log) {
		pos.set(x, y, z);
		BlockState old = level.getBlockState(pos);
		if (!old.isAir() && !old.canBeReplaced() && !(old.getBlock() instanceof LeavesBlock && log)) return;
		level.setBlock(pos, s, 2);
	}

	/** kind: 0 royal palm, 1 mangrove, 2 cypress, 3 pine, 4 coconut palm, 5 live oak, 6 sabal palm. */
	static void tree(WorldGenLevel level, BlockPos.MutableBlockPos pos, int kind, int x, int y, int z, int height) {
		pos.set(x, y, z);
		BlockState at = level.getBlockState(pos);
		if (!at.isAir() && !at.canBeReplaced() && !at.is(net.minecraft.world.level.block.Blocks.WATER)) return;
		switch (kind) {
			case 0 -> lookPalm(level, LookBlocks.Palm.ROYAL, x, y, z, Math.max(12, Math.min(22, height)));
			case 1 -> mangrove(level, pos, x, y, z, Math.max(5, Math.min(9, height * 2 / 3)));
			case 2 -> cypress(level, pos, x, y, z, Math.max(9, Math.min(20, height)));
			case 3 -> pine(level, pos, x, y, z, Math.max(10, Math.min(20, height)));
			case 4 -> lookPalm(level, LookBlocks.Palm.COCONUT, x, y, z, Math.max(7, Math.min(14, height)));
			case 6 -> lookPalm(level, LookBlocks.Palm.SABAL, x, y, z, Math.max(6, Math.min(12, height)));
			default -> oak(level, pos, x, y, z, Math.max(4, Math.min(7, height / 2)));
		}
	}

	private static final net.minecraft.core.Direction[] LEANS = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
			net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST};

	private static void lookPalm(WorldGenLevel level, LookBlocks.Palm kind, int x, int y, int z, int h) {
		long seed = ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
		net.minecraft.core.Direction lean = LEANS[(int) (Hash.h01(x, z, 5) * 4) & 3];
		LookBlocks.palm(level, new BlockPos(x, y, z), kind, h, lean, kind != LookBlocks.Palm.COCONUT || Hash.h01(x, z, 6) < 0.5, seed);
	}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

	private static void mangrove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		set(level, pos, x, y, z, WorldBlocks.MANGROVE_ROOTS, true);
		for (int[] d : DIRS) if (Hash.h01(x + d[0], z + d[1], 9) < 0.5) set(level, pos, x + d[0], y, z + d[1], WorldBlocks.MANGROVE_ROOTS, false);
		for (int k = 1; k < h; k++) set(level, pos, x, y + k, z, WorldBlocks.MANGROVE_LOG, true);
		int cy = y + h;
		for (int dy = -2; dy <= 1; dy++) {
			int rad = dy == 1 ? 2 : 3;
			for (int dx = -rad; dx <= rad; dx++)
				for (int dz = -rad; dz <= rad; dz++) {
					if (dx * dx + dz * dz > rad * rad + 1 || Hash.h01(x + dx, z + dz + dy * 17, 10) < 0.15) continue;
					set(level, pos, x + dx, cy + dy, z + dz, WorldBlocks.MANGROVE_LEAVES, false);
				}
		}
	}

	private static void cypress(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		for (int k = 0; k < h; k++) set(level, pos, x, y + k, z, WorldBlocks.CYPRESS_LOG, true);
		for (int k = h / 3; k <= h + 1; k++) {
			int rad = k > h - 2 ? 1 : (k - h / 3) % 3 == 0 ? 1 : 2;
			for (int dx = -rad; dx <= rad; dx++)
				for (int dz = -rad; dz <= rad; dz++) {
					if (dx * dx + dz * dz > rad * rad || Hash.h01(x + dx, z + dz + k * 13, 11) < 0.2) continue;
					set(level, pos, x + dx, y + k, z + dz, WorldBlocks.CYPRESS_LEAVES, false);
				}
		}
	}

	private static void pine(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		for (int k = 0; k < h; k++) set(level, pos, x, y + k, z, WorldBlocks.PINE_LOG, true);
		for (int k = h - 4; k <= h; k++) {
			int rad = k == h ? 1 : k == h - 4 ? 2 : 3;
			for (int dx = -rad; dx <= rad; dx++)
				for (int dz = -rad; dz <= rad; dz++) {
					if (dx * dx + dz * dz > rad * rad || Hash.h01(x + dx, z + dz + k * 7, 12) < 0.3) continue;
					set(level, pos, x + dx, y + k, z + dz, WorldBlocks.PINE_LEAVES, false);
				}
		}
	}

	private static void oak(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int h) {
		for (int k = 0; k < h; k++) set(level, pos, x, y + k, z, WorldBlocks.OAK_LOG, true);
		int cy = y + h;
		for (int dy = -1; dy <= 1; dy++) {
			int rad = dy == 1 ? 2 : 4;
			for (int dx = -rad; dx <= rad; dx++)
				for (int dz = -rad; dz <= rad; dz++) {
					if (dx * dx + dz * dz > rad * rad + 1 || Hash.h01(x + dx, z + dz + dy * 31, 99) < 0.12) continue;
					set(level, pos, x + dx, cy + dy, z + dz, WorldBlocks.OAK_LEAVES, false);
				}
		}
	}
}
