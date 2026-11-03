package dev.leonida.core.api;

/**
 * Terrain view the world module hands to other generators during chunk generation. Block coordinates
 * (x = map metres east, z = -map metres north, 1 block = 1 m). Implementations must be thread safe and
 * cheap: generators call them per column.
 */
public interface GenTerrain {
	/** Y of the top solid natural block (terrain, beach, seabed) at this column, before roads and buildings. */
	int groundY(int x, int z);

	/** Y of the water surface block at this column, or Integer.MIN_VALUE when the column is dry. */
	int waterY(int x, int z);

	/** Sea level Y of the Leonida dimension (map height 0 m). */
	int seaLevel();
}
