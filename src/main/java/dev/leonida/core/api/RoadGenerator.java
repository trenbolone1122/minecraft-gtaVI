package dev.leonida.core.api;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Contract between the world module (terrain, water, vegetation) and the road module (streets,
 * highways, bridges, markings, sidewalks, street furniture). The world module calls
 * {@link #generate} once per chunk after terrain and water, before buildings and landmarks. The road
 * module writes only inside the chunk and must be deterministic.
 */
public interface RoadGenerator {
	void generate(ChunkAccess chunk, GenTerrain terrain);

	/** True when the column is road surface, sidewalk or a road structure footprint (so world keeps trees and buildings off it). */
	default boolean claims(int x, int z) { return false; }

	/** Y of the top drivable surface at this column (deck of an elevated highway, or street surface), or Integer.MIN_VALUE. */
	default int deckY(int x, int z) { return Integer.MIN_VALUE; }

	RoadGenerator NONE = (chunk, terrain) -> {};

	final class Holder {
		private static volatile RoadGenerator instance = NONE;
		private Holder() {}
	}

	static RoadGenerator get() { return Holder.instance; }

	static void install(RoadGenerator g) { Holder.instance = g; }
}
