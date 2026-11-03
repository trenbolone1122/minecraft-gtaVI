package dev.leonida.core.api;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Hand-authored landmarks (Jason's house, Sahara Arena, Fleeca Field, hero towers, bridges...). The
 * world module calls {@link #generate} once per chunk last, after terrain, roads and buildings. Inside
 * a claimed site the world module skips generic buildings, infill and trees, so the landmark owns the
 * area. Landmarks write only inside the chunk and must be deterministic. Several landmark modules can
 * register; the world module calls all of them.
 */
public interface LandmarkGenerator {
	void generate(ChunkAccess chunk, GenTerrain terrain);

	/** True when (x, z) lies inside a landmark site (generic buildings, infill and trees are skipped there). */
	boolean claims(int x, int z);

	final class Registry {
		private static final java.util.List<LandmarkGenerator> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();
		private Registry() {}
	}

	static void register(LandmarkGenerator g) { Registry.ALL.add(g); }

	static java.util.List<LandmarkGenerator> all() { return Registry.ALL; }

	static boolean anyClaims(int x, int z) {
		for (LandmarkGenerator g : Registry.ALL) if (g.claims(x, z)) return true;
		return false;
	}

	/**
	 * Cheap conservative test: true when some claim may touch the box (inclusive block coordinates). The default
	 * checks every column; generators with site bounds override it with a bounds test.
	 */
	default boolean mayClaimBox(int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++)
			for (int z = z0; z <= z1; z++) if (claims(x, z)) return true;
		return false;
	}

	static boolean anyMayClaimBox(int x0, int z0, int x1, int z1) {
		for (LandmarkGenerator g : Registry.ALL) if (g.mayClaimBox(x0, z0, x1, z1)) return true;
		return false;
	}
}
