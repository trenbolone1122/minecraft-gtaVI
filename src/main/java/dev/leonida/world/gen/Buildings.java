package dev.leonida.world.gen;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.world.data.CwFeatures;
import dev.leonida.world.data.CwTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Levels each lot to its ground height and calls the build module per footprint (simple placeholder while none is installed). */
final class Buildings {
	private Buildings() {}

	private static final Logger LOG = LoggerFactory.getLogger("leonida-world");

	/** First air Y above finished ground for a building. */
	static int baseY(CwTerrain t, CwFeatures.Building b) {
		return Math.max(CwTerrain.SEA + 2, CwTerrain.SEA + (int) Math.round(b.groundM()) + 1);
	}

	static BuildingGenerator.Footprint footprint(CwTerrain t, CwFeatures.Building b) {
		return new BuildingGenerator.Footprint(b.id(), b.outer(), b.holes(), baseY(t, b), 0, b.height(), b.style(), b.kind(), null);
	}

	static void place(CwTerrain t, ChunkAccess chunk, RandomSource rnd) {
		ChunkPos cp = chunk.getPos();
		BuildingGenerator gen = BuildingGenerator.get();
		for (CwFeatures.Building b : CwFeatures.get().buildingsInChunk(cp.x(), cp.z())) {
			if (b.landmarkClaimed()) continue; // any column of the footprint, not only the centre
			BuildingGenerator.Footprint fp = footprint(t, b);
			level(t, chunk, b, fp.baseY());
			if (gen != BuildingGenerator.NONE) {
				try {
					gen.generate(chunk, fp, RandomSource.create(b.id() * 0x5DEECE66DL + 11));
				} catch (RuntimeException e) {
					LOG.error("BuildingGenerator failed for footprint {}", b.id(), e);
				}
			} else {
				placeholder(chunk, b, fp.baseY());
			}
		}
	}

	/** Flattens the lot: solid up to baseY - 1, air above it (up to the old terrain top). */
	private static void level(CwTerrain t, ChunkAccess chunk, CwFeatures.Building b, int base) {
		ChunkPos cp = chunk.getPos();
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState fill = WorldBlocks.concrete();
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				int x = x0 + lx, z = z0 + lz;
				if (!b.contains(x + 0.5, z + 0.5)) continue;
				int top = Math.max(t.groundY(x, z), t.waterY(x, z));
				for (int y = Math.min(top + 1, base - 4); y < base; y++) chunk.setBlockState(pos.set(x, y, z), fill);
				for (int y = base; y <= top; y++) chunk.setBlockState(pos.set(x, y, z), WorldBlocks.AIR);
			}
	}

	private static void placeholder(ChunkAccess chunk, CwFeatures.Building b, int base) {
		ChunkPos cp = chunk.getPos();
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		int h = Math.min(400, Math.max(3, b.height()));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				int x = x0 + lx, z = z0 + lz;
				if (!b.contains(x + 0.5, z + 0.5)) continue;
				boolean edge = !b.contains(x + 1.5, z + 0.5) || !b.contains(x - 0.5, z + 0.5)
						|| !b.contains(x + 0.5, z + 1.5) || !b.contains(x + 0.5, z - 0.5);
				if (edge) {
					for (int y = 0; y < h; y++) {
						int f = y % 4;
						boolean window = (f == 1 || f == 2) && ((x + z) & 3) != 0;
						chunk.setBlockState(pos.set(x, base + y, z), window ? WorldBlocks.PH_GLASS : WorldBlocks.PH_WALL);
					}
				}
				chunk.setBlockState(pos.set(x, base + h, z), WorldBlocks.PH_ROOF);
			}
	}
}
