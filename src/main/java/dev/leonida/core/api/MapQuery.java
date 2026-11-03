package dev.leonida.core.api;

import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Read access to the Leonida map (from the YANIS community map). The world agent installs the real
 * implementation with {@link #install}; until then a flat placeholder answers so other modules work.
 * Coordinates are block coordinates in the Leonida dimension; 1 block = 1 metre.
 */
public interface MapQuery {
	enum Surface { OCEAN, WATER, BEACH, LAND, PARK, ROAD, HIGHWAY, SIDEWALK, BUILDING, RUNWAY, SWAMP, RAIL }

	Surface surfaceAt(int x, int z);

	/** Ground (or water surface) Y at this column. */
	int groundY(int x, int z);

	/** ARGB colour for the minimap and the full map screen. */
	int mapColor(int x, int z);

	/** Road route for GPS and AI drivers; empty if none. */
	List<BlockPos> route(BlockPos from, BlockPos to);

	/** Named place at this position (district, landmark), or null. */
	String placeName(int x, int z);

	/** Nearest building of this kind (police station, hospital, shop...) as a ground-level entrance position, or null if unknown. Used for busted and wasted respawns and robbery targets. */
	default BlockPos nearest(BuildingGenerator.Kind kind, int x, int z) { return null; }

	MapQuery FLAT = new MapQuery() {
		public Surface surfaceAt(int x, int z) { return Surface.LAND; }
		public int groundY(int x, int z) { return 64; }
		public int mapColor(int x, int z) { return 0xFF6A8F5A; }
		public List<BlockPos> route(BlockPos from, BlockPos to) { return List.of(from, to); }
		public String placeName(int x, int z) { return null; }
	};

	final class Holder {
		private static volatile MapQuery instance = FLAT;
		private Holder() {}
	}

	static MapQuery get() { return Holder.instance; }

	static void install(MapQuery q) { Holder.instance = q; }
}
