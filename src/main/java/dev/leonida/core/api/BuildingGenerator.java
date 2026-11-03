package dev.leonida.core.api;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Contract between the world module (knows WHERE buildings stand) and the build module (knows WHAT
 * they look like inside and out). During chunk generation the world module calls
 * {@code BuildingGenerator.get().generate(...)} once per footprint touching the chunk; the build
 * module writes only blocks inside that chunk. Must be deterministic for a given footprint id.
 */
public interface BuildingGenerator {
	enum Kind { HOUSE, APARTMENT, TOWER, SHOP, GAS_STATION, MOTEL, CLUB, OFFICE, WAREHOUSE, BANK, HOTEL, MALL, POLICE, HOSPITAL, MARINA, LANDMARK }

	/**
	 * One building. polygon: outer ring in block x/z (closed, counter-clockwise); holes: inner rings (courtyards), may be
	 * empty. baseY: first floor block Y. floors: floor count. height: total height in blocks from baseY to roof top
	 * (buildings.json h rounded). style: facade style 0..5 from LOOK.md (0 glass tower, 1 white condo, 2 Art Deco pastel,
	 * 3 low-rise stucco, 4 grey commercial, 5 warehouse), or -1 to let the build module choose from kind. kind, stable id,
	 * optional landmark key (null if none).
	 */
	record Footprint(long id, List<int[]> polygon, List<List<int[]>> holes, int baseY, int floors, int height, int style,
			Kind kind, String landmark) {
		public static final int STYLE_AUTO = -1;

		/** Old shape: no holes, height from floors (3 blocks each), style chosen by the build module. */
		public Footprint(long id, List<int[]> polygon, int baseY, int floors, Kind kind, String landmark) {
			this(id, polygon, List.of(), baseY, floors, Math.max(3, floors * 3), STYLE_AUTO, kind, landmark);
		}
	}

	void generate(ChunkAccess chunk, Footprint fp, RandomSource random);

	BuildingGenerator NONE = (chunk, fp, random) -> {};

	final class Holder {
		private static volatile BuildingGenerator instance = NONE;
		private Holder() {}
	}

	static BuildingGenerator get() { return Holder.instance; }

	static void install(BuildingGenerator g) { Holder.instance = g; }
}
